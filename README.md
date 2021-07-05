![Java CI](https://github.com/BioDWH2/BioDWH2-GraphQL-Server/workflows/Java%20CI/badge.svg?branch=develop) ![Release](https://img.shields.io/github/v/release/BioDWH2/BioDWH2-GraphQL-Server) ![Downloads](https://img.shields.io/github/downloads/BioDWH2/BioDWH2-GraphQL-Server/total) ![License](https://img.shields.io/github/license/BioDWH2/BioDWH2-GraphQL-Server)

# BioDWH2-GraphQL-Server
| :warning: The GraphQL-Server is still experimental! For a stable solution, please try the [BioDWH2-Neo4j-Server](https://github.com/BioDWH2/BioDWH2-Neo4j-Server) or directly use the mapped GraphML file. |
| --- |

**BioDWH2** is an easy-to-use, automated, graph-based data warehouse and mapping tool for bioinformatics and medical informatics. The main repository can be found [here](https://github.com/BioDWH2/BioDWH2).

This repository contains the **BioDWH2-GraphQL-Server** utility which can be used to explore any BioDWH2 workspace using GraphQL queries. All necessary components are bundled with this tool.

## Usage
Once the workspace finished processing, the GraphQL-Server can be started as follows:
~~~BASH
> BioDWH2-GraphQL-Server.jar --start /path/to/workspace
~~~

Optionally, the port for the GraphQL-Server can be adjusted using the port command line argument.

## Help
~~~
Usage: BioDWH2-GraphQL-Server.jar [-h] [-bp=<boltPort>] [-c=<workspacePath>]
                                [-p=<port>] [-s=<workspacePath>]
  -h, --help          print this message
  -p, --port=<port>   Specifies the GraphQL server port (default 8090)
  -s, --start=<workspacePath>
                      Start a GraphQL server for the workspace
~~~