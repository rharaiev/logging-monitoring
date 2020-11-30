mvn -f books/pom.xml clean
mvn -f books/pom.xml package -Dtest=skip -DfailIfNoTests=false

mvn -f authors/pom.xml clean
mvn -f authors/pom.xml package -Dtest=skip -DfailIfNoTests=false

mvn -f web-sockets/pom.xml clean
mvn -f web-sockets/pom.xml package -Dtest=skip -DfailIfNoTests=false

mvn -f frontend/pom.xml clean
mvn -f frontend/pom.xml package -Dtest=skip -DfailIfNoTests=false

mvn -f zipkin-server/pom.xml clean
mvn -f zipkin-server/pom.xml package -Dtest=skip -DfailIfNoTests=false

docker build -t rharaiev/bff-zipkin-server:1.0 -f zipkin-server/Dockerfile zipkin-server
docker build -t rharaiev/bff-books-service:1.0 -f books/Dockerfile books
docker build -t rharaiev/bff-authors-service:1.0 -f authors/Dockerfile authors
docker build -t rharaiev/bff-web-sockets-service:1.0 -f web-sockets/Dockerfile web-sockets
docker build -t rharaiev/bff-frontend:1.0 -f frontend/Dockerfile frontend
