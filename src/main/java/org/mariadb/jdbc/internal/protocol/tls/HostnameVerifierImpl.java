package org.mariadb.jdbc.internal.protocol.tls;

import org.mariadb.jdbc.internal.logging.Logger;
import org.mariadb.jdbc.internal.logging.LoggerFactory;
import org.mariadb.jdbc.internal.util.Utils;

import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;
import javax.security.auth.x500.X500Principal;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.cert.Certificate;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.*;

public class HostnameVerifierImpl implements HostnameVerifier {

    private static Logger logger = LoggerFactory.getLogger(HostnameVerifierImpl.class);

    /**
     * DNS verification :
     * Matching is performed using the matching rules specified by
     * [RFC2459].  If more than one identity of a given type is present in
     * the certificate (e.g., more than one dNSName name, a match in any one
     * of the set is considered acceptable.) Names may contain the wildcard
     * character * which is considered to match any single domain name
     * component or component fragment. E.g., *.a.com matches foo.a.com but
     * not bar.foo.a.com. f*.com matches foo.com but not bar.com.
     *
     * @param hostname      hostname
     * @param tlsDnsPattern DNS pattern (may contain wildcard)
     * @return true if matching
     */
    private static boolean matchDns(String hostname, String tlsDnsPattern) throws SSLException {
        boolean hostIsIp = Utils.isIPv4(hostname) || Utils.isIPv6(hostname);
        StringTokenizer hostnameSt = new StringTokenizer(hostname.toLowerCase(), ".");
        StringTokenizer templateSt = new StringTokenizer(tlsDnsPattern.toLowerCase(), ".");
        if (hostnameSt.countTokens() != templateSt.countTokens()) return false;

        try {
            while (hostnameSt.hasMoreTokens()) {
                if (!matchWildCards(hostIsIp, hostnameSt.nextToken(), templateSt.nextToken())) return false;
            }
        } catch (SSLException exception) {
            throw new SSLException("host \"" + hostname + "\" doesn't correspond to certificate CN \"" + tlsDnsPattern
                    + "\" : wildcards not possible for IPs");
        }
        return true;
    }

    private static boolean matchWildCards(boolean hostIsIp, String hostnameToken, String tlsDnsToken) throws SSLException {
        int wildcardIndex = tlsDnsToken.indexOf("*");
        if (wildcardIndex != -1) {
            if (hostIsIp) throw new SSLException("WildCards not possible when using IP's");
            boolean first = true;
            String beforeWildcard;
            String afterWildcard = tlsDnsToken;

            while (wildcardIndex != -1) {
                beforeWildcard = afterWildcard.substring(0, wildcardIndex);
                afterWildcard = afterWildcard.substring(wildcardIndex + 1);

                int beforeStartIdx = hostnameToken.indexOf(beforeWildcard);
                if ((beforeStartIdx == -1) || (first && beforeStartIdx != 0)) return false;

                first = false;

                hostnameToken = hostnameToken.substring(beforeStartIdx + beforeWildcard.length());
                wildcardIndex = afterWildcard.indexOf("*");
            }
            return hostnameToken.endsWith(afterWildcard);
        }

        //no wildcard -> token must be equal.
        return hostnameToken.equals(tlsDnsToken);

    }

    static String extractCommonName(String principal) throws SSLException {
        if (principal == null) return null;
        try {
            LdapName ldapName = new LdapName(principal);

            for (Rdn rdn : ldapName.getRdns()) {
                if (rdn.getType().equalsIgnoreCase("CN")) {
                    Object obj = rdn.getValue();
                    if (obj != null) return obj.toString();
                }
            }
            return null;
        } catch (InvalidNameException e) {
            throw new SSLException("DN value \"" + principal + "\" is invalid");
        }
    }

    private static String normaliseAddress(String hostname) {
        try {
            if (hostname == null) return hostname;
            InetAddress inetAddress = InetAddress.getByName(hostname);
            return inetAddress.getHostAddress();
        } catch (UnknownHostException unexpected) {
            return hostname;
        }
    }

    private SubjectAltNames getSubjectAltNames(X509Certificate cert) throws CertificateParsingException {
        Collection<List<?>> entries = cert.getSubjectAlternativeNames();
        SubjectAltNames subjectAltNames = new SubjectAltNames();
        if (entries != null) {
            for (List<?> entry : entries) {
                if (entry.size() >= 2) {
                    int type = (Integer) entry.get(0);

                    if (type == 2) { //DNS
                        String altNameDns = (String) entry.get(1);
                        if (altNameDns != null) {
                            String normalizedSubjectAlt = altNameDns.toLowerCase(Locale.ROOT);
                            subjectAltNames.add(new GeneralName(normalizedSubjectAlt, Extension.DNS));
                        }
                    }

                    if (type == 7) { //IP
                        String altNameIp = (String) entry.get(1);
                        if (altNameIp != null) {
                            subjectAltNames.add(new GeneralName(altNameIp, Extension.IP));
                        }
                    }
                }
            }
        }
        return subjectAltNames;
    }

    @Override
    public boolean verify(String host, SSLSession session) {
        try {
            Certificate[] certs = session.getPeerCertificates();
            X509Certificate cert = (X509Certificate) certs[0];
            verify(host, cert);
            return true;
        } catch (SSLException ex) {
            if (logger.isDebugEnabled()) logger.debug(ex.getMessage(), ex);
            return false;
        }
    }

    /**
     * Verification that throw an exception with a detailed error message in case of error.
     *
     * @param host hostname
     * @param cert certificate
     * @throws SSLException exception
     */
    public void verify(String host, X509Certificate cert) throws SSLException {
        try {
            SubjectAltNames subjectAltNames = getSubjectAltNames(cert);
            if (!subjectAltNames.isEmpty()) {

                //***********************************************************
                // Host is IPv4 : Check corresponding entries in alternative subject names
                //***********************************************************
                if (Utils.isIPv4(host)) {
                    for (GeneralName entry : subjectAltNames.getGeneralNames()) {
                        if (entry.extension == Extension.IP) { //IP
                            if (host.equals(entry.value)) return;
                        }
                    }
                    throw new SSLException("No IPv4 corresponding to host \"" + host + "\" in certificate alt-names " + subjectAltNames.toString());
                }

                //***********************************************************
                // Host is IPv6 : Check corresponding entries in alternative subject names
                //***********************************************************
                if (Utils.isIPv6(host)) {
                    String normalisedHost = normaliseAddress(host);
                    for (GeneralName entry : subjectAltNames.getGeneralNames()) {
                        if (entry.extension == Extension.IP) { //IP
                            if (!Utils.isIPv4(entry.value)) {
                                String normalizedSubjectAlt = normaliseAddress(entry.value);
                                if (normalisedHost.equals(normalizedSubjectAlt)) {
                                    return;
                                }
                            }
                        }
                    }
                    throw new SSLException("No IPv6 corresponding to host \"" + host + "\" in certificate alt-names " + subjectAltNames.toString());
                }

                //***********************************************************
                // Host is not IP = DNS : Check corresponding entries in alternative subject names
                //***********************************************************
                String normalizedHost = host.toLowerCase(Locale.ROOT);
                for (GeneralName entry : subjectAltNames.getGeneralNames()) {
                    if (entry.extension == Extension.DNS) { //IP
                        String normalizedSubjectAlt = entry.value.toLowerCase(Locale.ROOT);
                        if (matchDns(normalizedHost, normalizedSubjectAlt)) {
                            return;
                        }
                    }
                }
                throw new SSLException("DNS host \"" + host + "\" not found in certificate alt-names " + subjectAltNames.toString());
            }
        } catch (CertificateParsingException cpe) {
            // ignore error
        }

        //***********************************************************
        // no alternative subject names, check using CN
        //***********************************************************
        X500Principal subjectPrincipal = cert.getSubjectX500Principal();
        String cn = extractCommonName(subjectPrincipal.getName(X500Principal.RFC2253));
        if (cn == null) {
            throw new SSLException("CN not found in certificate principal \"" + subjectPrincipal + "\"");
        }
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        String normalizedCn = cn.toLowerCase(Locale.ROOT);
        if (!matchDns(normalizedHost, normalizedCn)) {
            throw new SSLException("host \"" + normalizedHost + "\" doesn't correspond to certificate CN \"" + normalizedCn + "\"");
        }

    }

    private enum Extension {
        DNS, IP
    }

    private class GeneralName {
        String value;
        Extension extension;

        public GeneralName(String value, Extension extension) {
            this.value = value;
            this.extension = extension;
        }

        @Override
        public String toString() {
            return "{\"" + value + "\"|" + extension + "}";
        }
    }

    private class SubjectAltNames {
        List<GeneralName> generalNames = new ArrayList<>();

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("certificate SubjectAltNames[");
            boolean first = true;

            for (GeneralName generalName : generalNames) {
                if (!first) sb.append(",");
                first = false;
                sb.append(generalName.toString());
            }
            sb.append("]");
            return sb.toString();
        }

        public List<GeneralName> getGeneralNames() {
            return generalNames;
        }

        public void add(GeneralName generalName) {
            generalNames.add(generalName);
        }

        public boolean isEmpty() {
            return generalNames.isEmpty();
        }
    }
}
