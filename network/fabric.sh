#!/usr/bin/env bash
# simple batch script making it easier to cleanup and start a relatively fresh fabric env.

if [ ! -e "docker-compose.yaml" ];then
  echo "docker-compose.yaml not found."
  exit 8
fi

function dkrm(){
        DOCKER_IMAGE_IDS=$(docker images | grep "dev\|none\|test-vp\|peer[0-9]-" | awk '{print $3}')
	echo
        if [ -z "$DOCKER_IMAGE_IDS" -o "$DOCKER_IMAGE_IDS" = " " ]; then
		echo "========== No images available for deletion ==========="
        else
                docker rmi -f $DOCKER_IMAGE_IDS
        fi
	echo
}

function clean(){

  rm -rf /var/hyperledger/*

  if [ -e "samplestore.properties" ];then
    rm -f "samplestore.properties"
  fi

  lines=`docker ps -a | grep 'dev-peer' | wc -l`

  if [ "$lines" -gt 0 ]; then
    docker ps -a | grep 'dev-peer' | awk '{print $1}' | xargs docker rm -f
  fi

  lines=`docker images | grep "dev\|none\|test-vp\|peer[0-9]-" | wc -l`
  if [ "$lines" -gt 0 ]; then
    docker images | grep 'dev-peer' | awk '{print $1}' | xargs docker rmi -f
  fi

  docker-compose kill
  docker-compose rm -f

  rm ../samplestore.properties 2> /dev/null
}

function up(){
  docker-compose up -d
}

function down(){
  docker-compose down;
}


for opt in "$@"
do

    case "$opt" in
        up)
            up
            ;;
        down)
            down
            ;;
        clean)
            clean
            ;;
        restart)
            down
            clean
            up
            ;;

        *)
            echo $"Usage: $0 {up|down|clean|restart}"
            exit 1

esac
done