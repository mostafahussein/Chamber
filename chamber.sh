#!/bin/bash

NAME=Chamber
DESC=Chamber


function service_cmd() {

local service_args=($*)

case $service_args in
    clean)
        CLEAN="mvn -DskipTests clean"
        echo "Running ${CLEAN}"
        $CLEAN
        result=$?
        if [[ $result > 0 ]]; then
            exit 1;
        fi
        ;;

    package)
        PACKAGE="mvn -DskipTests -Pallinone package"
        echo "Running ${PACKAGE}"
        $PACKAGE
        result=$?
        if [[ $result > 0 ]]; then
            exit 1;
        fi
        ;;
        
    start)
        echo "Starting $DESC"
        shift

        CONTEXT=src/main/resources/jetty.xml
        TARGET=target/chamber-0.1-SNAPSHOT-allinone.jar
        MAIN=com.echoed.chamber.Main

        NEWRELIC=/opt/newrelic/newrelic.jar

#        PACKAGE="mvn -DskipTests -Pallinone package"
        CLASSPATH=".:${TARGET}"
        OVERRIDES="src/overrides/resources"
        ARGS_INTERESTING="-XX:+CMSPermGenSweepingEnabled -XX:+CMSClassUnloadingEnabled"
        ARGS="-server -Xms1024m -Xmx2048m -XX:PermSize=256m  -Djava.net.preferIPv4Stack=true -Dsun.net.client.defaultConnectTimeout=5000 -Dsun.net.client.defaultReadTimeout=5000"


        if [[ "$1" == "-o" ]]; then
            CLASSPATH="${OVERRIDES}:${CLASSPATH}"
            shift
        fi

        if [[ "$1" == "-d" ]]; then
            ARGS="$ARGS -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=8000"
            shift
        fi

        if [ -e ${NEWRELIC} ]; then
            ARGS="-javaagent:${NEWRELIC} $ARGS"
        fi

        if [ ! -e ${TARGET} ]; then
            echo "Missing ${TARGET}"
            service_cmd "package"
        fi

        if [ ! ${1} ]; then
            echo "Using default Spring context configuration ${CONTEXT}"
        else
            CONTEXT=${1}
        fi
        
        echo "java ${ARGS} -cp ${CLASSPATH} ${MAIN} ${CONTEXT}"

        # We do this to capture the pid of the process
        sh -c "java ${ARGS} -cp ${CLASSPATH} ${MAIN} ${CONTEXT} >./std.out 2>&1 & APID=\"\$!\"; echo \$APID > chamber.pid"
        ;;
    
    startt)
        service_cmd "start"
        tail -f std.out
        ;;

    stop)
        shift
        echo "Stopping $DESC"
        kill -9 `cat chamber.pid`
        ;;

    restart)
        service_cmd "stop"
        service_cmd "start"
        ;;
    
    reload)
        service_cmd "clean"
        service_cmd "package"
        service_cmd "restart"
        ;;

    verify)
        shift
        echo "Running integration tests for $DESC"
        displaycmd=""
        if [[ "$1" == "-d" ]]; then
            displyacmd="-DdisplayCmd=true"
            shift
        fi

        mvn $displaycmd -Pitest verify
        ;;

    scalatest)
        shift
        display=""
        if [[ "$1" == "" ]]; then
            display="-eNDXEHLO"
        fi

        #See http://www.scalatest.org/user_guide/using_the_runner for command line options 
        #Add -DdisplayCmd=true to see command used...
        mvn scala:run -Dlauncher=scalatest -DaddArgs="$display"
        ;;

    console)
        rlwrap mvn scala:console
        ;;

    targz)
        rm chamber.tar.gz
        rm target/chamber-0.1-SNAPSHOT-allinone.jar
        mvn -DskipTests -Pallinone clean package
        tar -cvzf chamber.tar.gz --exclude chamber.pid --exclude chamber.iml --exclude std.out --exclude out --exclude-vcs --exclude-backups *
        ;;

    install)
        sudo apt-get install mysql-server
        #Install partner repo for distribution in /etc/apt/sources.list
        #deb http://archive.canonical.com/ubuntu natty partner
        #deb-src http://archive.canonical.com/ubuntu natty partner
        sudo apt-get install sun-java6-jdk
        ;;
    
    status)
        ps uh -p `cat chamber.pid`
        ;;

    *)
        echo "Usage: $NAME {start|stop|restart|reload|status|verify|scalatest|console|targz|package|clean}" >&2
        exit 1
        ;;
esac
}

CMD=$*
service_cmd ${CMD[*]}

exit $?







