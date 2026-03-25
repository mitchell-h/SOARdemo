# Stock Market Demo

## General Instructions
- Do NOT EVER unless expressly told, use an emoji
- You are an expert Littlehorse.io developer.
- You are building a SOAR(Security Orchestration, Automation, and Response) demo application to showcase LittleHorse.io and it's ability to intergrate different platforms and services in response to an event.
- Store all data in flat files in JSON format.  Avoid databases.
- Any webUIs should be created in javelin framework. 
- LittleHorse.io workflows should orchistrate all interservice communication
- Language of choice is Java
- There should be around 3000 accounts and 2000 users tied to those accounts.
- add as many comments to the code as is reasonable to explain what the code is doing.


## Project overview

## Core Features
- **Logs API:** an API that retreives historical logs.  on start historical logs based off a sample data set should be loaded into the logs service.  The service should have the ability to search historical logs by feild in a json object.This should be an HTTP API
- **Core banking or user and account service:** This service will need to provide username to account, balance information, previous country of origin, address.  The data should be preloaded on boot from a sample dataset.  It should also have the ability to freeze and unfreezea an account.  This should be an HTTP API.
- **Admin Interface:**  there should an admin interface that allows for searching logs, and viewing any alerts
- **Mock Fraud detection engine:** This service will take in information, and return a score of likelyness of fraudulant activity.  This should be an HTTP api.
- **Check and card verification service:** This service will verify that a credit card or check, tied to an account in the core banking service, is valid.  It should also have the ability to freeze and unfreezea an account.
- **Data Generation service:** There should a service that generates data to the logs api, as well as produces valid "alerts".  This service should use the mock data to further generate realistic logs and events
- **Case management:** there should a service that keeps track of all suspected fraud.  When an account is frozen a new case should be created with as many details as possible as to why, who, what and where they came from.  When a case is closed the case should be closed with as much detail as possible as to why, who, what and where they came from.  Past cases should be referenced in new cases