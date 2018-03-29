export localDataPath="${1:-/Users/freefly/temp/english-words}"
sbt clean docker:publishLocal

docker kill english-words || true
docker rm english-words || true

docker run -d --restart unless-stopped -e "APP_SECRET=<APP_SECRET_KEY>" -v $localDataPath:/data -p 9000:9000 --name=english-words english-words:1.0-SNAPSHOT
