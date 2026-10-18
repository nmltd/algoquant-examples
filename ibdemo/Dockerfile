FROM nmfintech/tws-ibcontroller:latest
ADD --chown=trader ./app /home/trader/app
ADD --chown=trader ./resources/IBController /home/trader/IBController
USER trader
WORKDIR /home/trader/app
EXPOSE 8080 5900
ENTRYPOINT ["./run.sh"]

