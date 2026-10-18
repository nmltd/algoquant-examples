call mvn -f ../pom.xml -Dmaven.javadoc.skip=true -DskipTests clean install spring-boot:repackage
rmdir /S /Q app
mkdir app
xcopy ..\target\algoquant-ib-app.jar .\app
xcopy ..\numericalmethod.lic .\app
xcopy ..\private_key.json .\app
xcopy .\resources\run.sh .\app
docker build -t ibdemo .
