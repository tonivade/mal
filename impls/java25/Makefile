# Copyright (c) 2024-2025, Antonio Gabriel Muñoz Conejo <me AT tonivade DOT es>
# Distributed under the terms of the MIT License

GRADLE := ./gradlew
JAR_NAME := mal.jar
GRADLE_JAR := build/libs/mal-0.1-SNAPSHOT-all.jar
CLASS_DIR := build/classes/java/main

.PHONY: all
all: $(JAR_NAME)

$(GRADLE_JAR):
	$(GRADLE) build

$(JAR_NAME): $(GRADLE_JAR)
	cp $< $@

$(CLASS_DIR)/mal/step%.class: $(GRADLE_JAR)
	@true

.PHONY: dist
dist: $(JAR_NAME)

.PHONY: clean
clean:
	rm -f $(JAR_NAME)
	$(GRADLE) clean
