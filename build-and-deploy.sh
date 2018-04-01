sbt clean docker:publishLocal

docker kill english-words || true
docker rm english-words || true
docker kill postgres-english-words || true
docker rm postgres-english-words || true

docker run -d --name postgres-english-words -e "POSTGRES_DB=english-words" -v "$(pwd)"/../english-words-data:/var/lib/postgresql/data postgres
docker run -d --restart unless-stopped --link postgres-english-words -e "APP_SECRET=<APP_SECRET_KEY>" -e "POSTGRES_URL=jdbc:postgresql://postgres-english-words:5432/english-words" -p 9000:9000 --name=english-words english-words:1.0-SNAPSHOT
