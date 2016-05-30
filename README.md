# SonarQube Mercurial plugin
[![Build Status](https://travis-ci.org/SonarQubeCommunity/sonar-scm-mercurial.svg)](https://travis-ci.org/SonarQubeCommunity/sonar-scm-mercurial)

## Description
This plugin implements SCM dependent features of SonarQube for [Mercurial](http://www.mercurial-scm.org/) projects.

## Requirements
* The Mercurial command line tool (hg) must be available in the path.
* Version 2.1+ of Mercurial is required because of the use of -w flag.

## Usage
Auto-detection will works if there is a .hg folder in the project root directory. Otherwise you can force the provider using -Dsonar.scm.provider=hg.

## Developper informations
The plugin doesn't use [Hg4j](http://www.hg4j.com/) because it is not available on Maven central.
