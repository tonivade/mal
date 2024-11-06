all:
	./gradlew build

dist: mal.jar

mal.jar: all
	cp build/libs/mal-0.1-SNAPSHOT-all.jar $@

build/classes/java/main/mal/step%.class: 
	./gradlew build

clean:
	./gradlew clean
	rm -f mal.jar