#!/bin/bash
cd "$(dirname "$0")"
java -version 2>&1 | head -1 >/dev/null || { echo "Need Java 21+"; exit 1; }
[ ! -f "your-business-app/target/classes/com/your/business/AgentApplication.class" ] && { echo "Compiling..."; mvn clean install -Dmaven.test.skip=true -pl your-agent-core,your-agent-spring-boot-starter,your-business-app -am -q; }
echo "Phoenix starting..."
mvn spring-boot:run -pl your-business-app -Dspring-boot.run.profiles=sensenova
