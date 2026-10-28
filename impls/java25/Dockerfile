# Copyright (c) 2024-2025, Antonio Gabriel Muñoz Conejo <me AT tonivade DOT es>
# Distributed under the terms of the MIT License
FROM ubuntu:24.04

RUN apt-get -y update

# Required for running tests
RUN apt-get -y install make python3
RUN ln -fs /usr/bin/python3 /usr/local/bin/python

RUN mkdir -p /mal
WORKDIR /mal

# Installed system-wide because the tests run as the host user, not as a user of this image
RUN apt-get -y install openjdk-25-jdk-headless

# Gradle and the REPL history write to the home directory, which must be writable by any user
ENV HOME=/mal
