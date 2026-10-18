#!/bin/sh
export VNC_PASSWORD=changeme

Xvfb :1 -screen 0 1600x1200x16 &
export DISPLAY=:1.0
/opt/IBController/IBControllerStart.sh &
/usr/bin/x11vnc -ncache 10 -passwd $VNC_PASSWORD -display :1 -forever -bg
sleep 1m
java -jar algoquant-ib-app.jar
