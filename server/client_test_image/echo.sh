#!/bin/sh

# Simple script that echoes the line of text provided as argument or on stdin. If the line starts with the string
# "ERR", the text will be output on stderr, and on stdout otherwise.
#
# For testing purpose.

print_line() {
  case "$1" in
    ERR*) echo $1 1>&2 ;;
    *)    echo $1 ;;
  esac
}

for LINE in "$@"
do
  print_line "${LINE}"
done

while read LINE ; do
  print_line "${LINE}"
done