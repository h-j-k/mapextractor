#!/bin/bash
lgm() { date "+%c%t$1"; }
[[ "$TRAVIS_PULL_REQUEST" != false || "$TRAVIS_BRANCH" != master ]] \
	&& exit || lgm "Generating Javadocs..."
myid=${TRAVIS_REPO_SLUG%/*}
myproj=${TRAVIS_REPO_SLUG##*/}
jdb=gh-pages
websrc=$TRAVIS_BUILD_DIR/target/site/apidocs
webtgt=$(mktemp -d)
[ -z "$webtgt" ] && lgm "Error creating tmp dir, bailing." && exit 1
mvn javadoc:javadoc
cd $HOME
git config --global user.email "travis@travis-ci.org"
git config --global user.name "travis-ci"
lgm Cloning... 
	git clone -b $jdb https://${gh_token}@github.com/$myid/$myproj "$webtgt" # > /dev/null
	# git clone -qb $jdb https://${gh_token}@github.com/$myid/$myproj "$webtgt" > /dev/null
lgm Copying...
	rsync -ivr --delete "$websrc" "$webtgt"
	# rsync -qr --delete "$websrc" "$webtgt"
lgm Adding...
	git --git-dir="$webtgt/$jdb" add -f . # > /dev/null
lgm Committing...
	git commit -m "Published Javadoc: build $TRAVIS_BUILD_NUMBER" # > /dev/null
lgm Pushing... 
	git push -f origin $jdb # > /dev/null
	# git push -fq origin $jdb > /dev/null
lgm Done.