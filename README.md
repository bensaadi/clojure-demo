### Demo
https://dd5mdsv9dim2j.cloudfront.net/

### Synopsis
This is part of an admin panel to manage delivery logistics from warehouses to lockers and back. 

A delivery driver must deliver parcels to a fixed number of locker locations, and retrieve returns on each stop.
The goal is to schedule the optimal routes given time and capacity constraints.

This program provides a management user interface for solving the Capacitated Vehicle Routing Problem with Time Windows (CVRPTW).

### Architecture

This repo consists of:
- A Clojure backend that hosts a REST API;
- A ClojureScript front-end with reagent and re-frame, interacting with the backend via AJAX;
- Interop with java to run the CVRPTW algorithm provided by Google's OR-TOOLS.