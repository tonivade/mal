all:
	./gradlew build

dist: mal.jar

mal.jar:
	./gradlew shadowJar
	cp build/libs/mal-0.1-SNAPSHOT-all.jar $@

clean:
	./gradlew clean
	rm -f mal.jar
