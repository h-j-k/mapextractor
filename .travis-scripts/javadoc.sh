#!/bin/bash
[[ ! ("$TRAVIS_PULL_REQUEST" == false && "$TRAVIS_BRANCH" == master) ]] \
	&& exit || echo Generating Javadocs...
mvn javadoc:javadoc
ls -l target/site/apidocs