#!/bin/sh
MIRROR=www.eu.apache.org/dist
#MIRROR=http://mirror2.klaus-uwe.me/apache

wget $MIRROR/pdfbox/1.8.13/pdfbox-1.8.13.jar
wget $MIRROR/pdfbox/1.8.13/fontbox-1.8.13.jar
wget $MIRROR/pdfbox/1.8.13/pdfbox-1.8.13-src.zip
wget $MIRROR/commons/logging/binaries/commons-logging-1.2-bin.tar.gz
tar -xzf commons-logging-1.2-bin.tar.gz commons-logging-1.2/commons-logging-1.2.jar
mv commons-logging-1.2/commons-logging-1.2.jar .
rmdir commons-logging-1.2/

