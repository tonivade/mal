# Copyright (c) 2024-2025, Antonio Gabriel Muñoz Conejo <me AT tonivade DOT es>
# Distributed under the terms of the MIT License
FROM ubuntu:24.04

USER root

RUN apt-get -y update

# Required for running tests
RUN apt-get -y install make python3 zip unzip curl
RUN ln -fs /usr/bin/python3 /usr/local/bin/python

RUN mkdir /mal
RUN chown ubuntu:ubuntu /mal
WORKDIR /mal

USER ubuntu

# Downloading SDKMAN!
RUN curl -s "https://get.sdkman.io" | bash
RUN bash -c "source $HOME/.sdkman/bin/sdkman-init.sh && \
    yes | sdk install java 25.0.2-tem && \
    rm -rf $HOME/.sdkman/archives/* && \
    rm -rf $HOME/.sdkman/tmp/*"
