#!/bin/sh

while read LINE ; do
  case "${LINE}" in
    ERR*) echo ${LINE} 1>&2 ;;
    *)    echo ${LINE} ;;
  esac
done
