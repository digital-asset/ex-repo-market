Repo Market: Example DAML Application
=====================================

::

  Copyright (c) 2019, Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
  SPDX-License-Identifier: Apache-2.0

This document details an example DAML workflow for a repo market clearing house.

In this example:

A `Repo Trading Model <docs/repo-trading-model.rst>`_
  This section describes the business processes involved in trading Repurchase Agreements, 'Repos'.
`DAML Implementation <docs/daml-implementation.rst>`_
  This section describes how these business processes are modelled, and implemented in DAML.
`Automation description <docs/automation-description.rst>`_
  This section describes how the DAML-modeled processes can be automated

Building the example
--------------------

**Disclaimer:** This reference application is intended to demonstrate the capabilities of the DAML. You are recommended to consider other non-functional aspects, like security, resiliency, recoverability, etc prior to production use.

Prerequisites:

* You have Maven and DAML SDK installed installed and available

1. Open a terminal window and change to the root directory of the repository
2. Type ``mvn clean package``. This creates the application DAR and JAR in the ``target`` folder

Running the example
-------------------

Running with JVM
################

Prerequisites:

* You have a Java 8 JRE installed, with `java` on your PATH
* You have the DAML SDK installed
* You have created the example project as detailed above

1. Open a terminal window
2. Change to the example folder: ``cd repo-market``
3. Run ``daml start``
4. Open a new terminal window and change to the example folder
5. Run the command ``./scrips/start.sh``.

.. code-block:: bash

  $ ./scripts/start.sh

This will start the system with a default trade file ``data/Trades12-2018-06-28.csv``. To run with another trade file add that file as an argument to the start script: 

.. code-block:: bash

  $ ./scripts/start.sh <your_trade_file>

Several example trade files are included in the ``data/`` folder.

The script will start a Sandbox and Navigator, and run all automation processes necessary, logging progress output to the terminal. 

The trades in the given trade file will load, and the system will pause, waiting for a command.

Stop the system at any time by typing the interrupt character (Cntl-C) in the terminal windows.

Running with docker
###################

Prerequisites:
* You have Docker and Docker-Compose installed

1. Open a terminal window
2. Change to the example folder: ``cd repo-market``
3. Run the following docker-compose command:

.. code-block:: bash

  $ docker-compose up --build

This will start all the components in docker containers and once they loaded you can access the application by browsing to http://localhost:7500/.

Controlling the application
---------------------------

The example has a simple command-line interface to control the application. With this interface, you can;

- list trades available for settlement on any date
- initiate settlement for a given date
- load another set of trades from a file

To run these commands, use a new terminal window:

1. Open a terminal window
2. Change to the example folder: ``cd repo-market`` 

List trades available for settlement
####################################

At the command promt, type ``./scripts/listTrades.sh``. The response will be a list of dates and the trades available for settlement:

. code-block:: bash

  $ ./scripts/listTrades.sh
  2018-06-28 12
  2018-11-26 120

Start settlement for a given date
#################################

Using script
~~~~~~~~~~~~

At the command prompt, type ``./scripts/doSettlement.sh <date>``, where ``<date>`` is the settlement date. This should be of teh form ``YYYY-MM-DD``, as returned from the ``listTrades.sh`` command.

The system will then proceed to settlement, and log output to the terminal. The output will appear as in the figure below.

.. figure:: img/Trades1Output.png

Trade files may contain trades with different settlement dates, and the system will allow multiple settlement runs without restarting. If no trades are available for a given settlement date, a message will be printed to the terminal.

 On completion, the comand will print ``Settled``

.. code-block:: bash

  $ ./scripts/doSettlement.sh 2018-06-28
  Settled

Using Navigator
~~~~~~~~~~~~~~~

1. Log in as ``CCP``
2. Select the ``CCP Role`` tab.
3. Select the contract
4. Exercise the choice ``InitiateSettlement`` with the given date (2018-06-28)


Load another trade file
#######################

At the command prompt, type ``./scripts/loadTradeFile.sh <filename>`` where ``filename`` is the file. This path must be absolute, or relative to the current directory.

The command will load the file in all trading participants, which you will see reported in the terminal window where the application is running. On completion, the comand will print ``Injected`` for each participant.

.. code-block:: bash

  $ ./scripts/loadTradeFile.sh data/Trades120-2018-11-26.csv 
  Injected
  Injected
  Injected
  Injected

Setting the injection delay
~~~~~~~~~~~~~~~~~~~~~~~~~~~

To allow the workflow to be observed, trade participants delay for fixed delay before creating a ``RegistrationRequest``. This delay has a default value of 2 seconds, and can be set with a ``-d`` command line option to the star script ``./scripts/start.sh``. The delay is expressed in milliseconds. For example, to set the delay to half a second (500 mS), do:

.. code-block:: bash

  $ ./scripts/start.sh -d 500

Adding trading parties
~~~~~~~~~~~~~~~~~~~~~~

The example reads a system configuration from the file ``config.yaml`` - you can add parties by updating this file. Make sure to define a name and new port number for the participant (see the existing file for the format). You can then create trade records for those parties in a new, or existing trade file. 

Note that you will also need to add these new parties to the DA project file ``da.yaml``.

Next: `Repo Trading Model <docs/repo-trading-model.rst>`_.

