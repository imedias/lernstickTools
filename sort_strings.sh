#!/bin/sh
for i in src/main/resources/ch/fhnw/util/Strings*
do
	sort $i>tmp
	mv tmp $i
done
