# Copyright (c) 2024, Antonio Gabriel Muñoz Conejo <me AT tonivade DOT es>
# Distributed under the terms of the MIT License
all:
	./gradlew build

dist: mal.jar

mal.jar: all
	cp build/libs/mal-0.1-SNAPSHOT-all.jar $@

build/classes/java/main/mal/step%.class:
	echo nothing to do

clean:
	rm -f mal.jar