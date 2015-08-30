#!/bin/bash
lgm() { date "+%c"$'\t'"$1"$'\n'; }
[[ "$TRAVIS_PULL_REQUEST" != false || "$TRAVIS_BRANCH" != master ]] \
	&& exit || lgm "Generating Javadocs..."
myid=h-j-k
myproj=mapextractor
jdb=gh-pages
websrc=$TRAVIS_BUILD_DIR/target/site/apidocs
webtgt=$jdb
mvn javadoc:javadoc
git config --global user.email "travis@travis-ci.org"
git config --global user.name "travis-ci"
lgm "Cloning..."
cd $HOME
git clone --quiet --branch=$jdb https://${gh_token}@github.com/$myid/$myproj $webtgt > /dev/null
pushd $webtgt
lgm "Copying..."; rm -rf apidocs && cp -Rf $websrc .
lgm "Adding..."; git add -f . > /dev/null
lgm "Committing..."; git commit -m "Published Javadoc: build $TRAVIS_BUILD_NUMBER" > /dev/null
lgm "Pushing..."; git push -fq origin $jdb > /dev/null
lgm "Done."
