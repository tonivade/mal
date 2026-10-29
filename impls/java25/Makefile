# Copyright (c) 2024-2025, Antonio Gabriel Muñoz Conejo <me AT tonivade DOT es>
# Distributed under the terms of the MIT License

SHELL := /bin/bash
GRADLE := ./gradlew
JAR_NAME := mal.jar
GRADLE_JAR := build/libs/mal.jar
CLASS_DIR := build/classes/java/main
SOURCES := $(shell find src/main -type f) build.gradle settings.gradle gradle.properties

.PHONY: all
all: $(JAR_NAME)

# only the jar is built here, the unit tests run with `make test`
# touch the jar so gradle's up-to-date checks don't leave it older than its sources
$(GRADLE_JAR): $(SOURCES)
	$(GRADLE) shadowJar
	touch $@

$(JAR_NAME): $(GRADLE_JAR)
	cp $< $@

$(CLASS_DIR)/mal/step%.class: $(JAR_NAME)
	@true

.PHONY: dist
dist: $(JAR_NAME)

.PHONY: test
test:
	$(GRADLE) test

.PHONY: clean
clean:
	rm -f $(JAR_NAME)
	$(GRADLE) clean
