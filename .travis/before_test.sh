#!/bin/bash

set -x
set -e

remove_mysql(){
    sudo service mysql stop
    sudo apt-get -qq autoremove --purge mysql-server mysql-client mysql-common
    sudo rm -rf /etc/mysql||true
    sudo rm -rf /var/lib/mysql||true
}
remove_mysql

if [ -n "$AURORA" ]
then
    # AURORA tests doesn't need an installation
    echo "$MYSQL"
else
    if [ -n "$MYSQL" ]
    then
        sudo tee /etc/apt/sources.list.d/mysql.list << END
deb http://repo.mysql.com/apt/ubuntu/ precise mysql-$MYSQL
deb-src http://repo.mysql.com/apt/ubuntu/ precise mysql-$MYSQL
END
        sudo apt-key adv --keyserver pgp.mit.edu --recv-keys 5072E1F5

        sudo apt-get -qq update --force-yes
        sudo apt-get -qq install mysql-server --force-yes

        dpkg -l|grep ^ii|grep mysql-server|grep ${MYSQL/-dmr/}

    else

        sudo apt-get -qq install python-software-properties

        sudo apt-key adv --recv-keys --keyserver hkp://keyserver.ubuntu.com:80 0xcbcb082a1bb943db
        sudo add-apt-repository "deb [arch=amd64,i386] http://nyc2.mirrors.digitalocean.com/mariadb/repo/${MARIA}/ubuntu precise main"

        sudo apt-get -qq update

        sudo apt-get -qq install mariadb-server
    fi

    INNODB_LOG_FILE_SIZE=$(echo $PACKET| cut -d'M' -f 1)0M
    sudo tee /etc/mysql/conf.d/map.cnf << END
[mysqld]
max_allowed_packet=$PACKET
innodb_log_file_size=$INNODB_LOG_FILE_SIZE
END

    # Generate SSL files:
    sudo .travis/gen-ssl.sh mariadb.example.com /etc/mysql
    sudo chown mysql:mysql /etc/mysql/*.crt /etc/mysql/*.key /etc/mysql/*.p12

    # Enable SSL:
    sudo tee /etc/mysql/conf.d/ssl.cnf << END
[mysqld]
ssl-ca=/etc/mysql/ca.crt
ssl-cert=/etc/mysql/server.crt
ssl-key=/etc/mysql/server.key
END

    sudo mysql -u root -e "SET GLOBAL innodb_fast_shutdown = 1"
    sudo mysql -u root -e "update mysql.user set plugin = 'mysql_native_password' where User = 'root' and Host = 'localhost'"

    sudo service mysql stop
    #Adding sleep time for clean shutdown
    if [ "x$MYSQL" != "x" ]
    then
        sleep 2
    fi
    sudo rm -f /var/lib/mysql/ib_logfile*
    sudo service mysql start

    #Adding sleep time if mysql DB. If not SSL not totally initialized when launching tests
    if [ "x$MYSQL" != "x" ]
    then
        sleep 20
    fi
    sudo mysql -uroot -e "create database IF NOT EXISTS testj"

fi


