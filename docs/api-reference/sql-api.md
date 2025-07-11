---
id: sql-api
title: Druid SQL API
sidebar_label: Druid SQL
---
import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

<!--


  ~ Licensed to the Apache Software Foundation (ASF) under one
  ~ or more contributor license agreements.  See the NOTICE file
  ~ distributed with this work for additional information
  ~ regarding copyright ownership.  The ASF licenses this file
  ~ to you under the Apache License, Version 2.0 (the
  ~ "License"); you may not use this file except in compliance
  ~ with the License.  You may obtain a copy of the License at
  ~
  ~   http://www.apache.org/licenses/LICENSE-2.0
  ~
  ~ Unless required by applicable law or agreed to in writing,
  ~ software distributed under the License is distributed on an
  ~ "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
  ~ KIND, either express or implied.  See the License for the
  ~ specific language governing permissions and limitations
  ~ under the License.
  -->

:::info
 Apache Druid supports two query languages: Druid SQL and [native queries](../querying/querying.md).
 This document describes the SQL language.
:::

In this topic, `http://ROUTER_IP:ROUTER_PORT` is a placeholder for your Router service address and port. Replace it with the information for your deployment. For example, use `http://localhost:8888` for quickstart deployments.

## Query from Historicals

### Submit a query

Submits a SQL-based query in the JSON or text format request body. 
Returns a JSON object with the query results and optional metadata for the results. You can also use this endpoint to query [metadata tables](../querying/sql-metadata-tables.md).

Each query has an associated SQL query ID. You can set this ID manually using the SQL context parameter `sqlQueryId`. If not set, Druid automatically generates `sqlQueryId` and returns it in the response header for `X-Druid-SQL-Query-Id`. Note that you need the `sqlQueryId` to [cancel a query](#cancel-a-query).

#### URL

`POST` `/druid/v2/sql`

#### JSON Format Request body

To send queries in JSON format, the `Content-Type` in the HTTP request MUST be `application/json`.
If there are multiple `Content-Type` headers, the **first** one is used.

The request body takes the following properties:

* `query`: SQL query string. HTTP requests are permitted to include multiple `SET` statements to assign [SQL query context parameter](../querying/sql-query-context.md) values to apply to the query statement, see [SET](../querying/sql.md#set) for details. Context parameters set by `SET` statements take priority over values set in `context`.
* `resultFormat`: String that indicates the format to return query results. Select one of the following formats:
  * `object`: Returns a JSON array of JSON objects with the HTTP response header `Content-Type: application/json`.  
     Object field names match the columns returned by the SQL query in the same order as the SQL query.

  * `array`: Returns a JSON array of JSON arrays with the HTTP response header `Content-Type: application/json`.  
     Each inner array has elements matching the columns returned by the SQL query, in order.

  * `objectLines`: Returns newline-delimited JSON objects with the HTTP response header `Content-Type: text/plain`.  
     Newline separation facilitates parsing the entire response set as a stream if you don't have a streaming JSON parser.
     This format includes a single trailing newline character so you can detect a truncated response.

  * `arrayLines`: Returns newline-delimited JSON arrays with the HTTP response header `Content-Type: text/plain`.  
     Newline separation facilitates parsing the entire response set as a stream if you don't have a streaming JSON parser.
     This format includes a single trailing newline character so you can detect a truncated response.

  * `csv`: Returns comma-separated values with one row per line. Sent with the HTTP response header `Content-Type: text/csv`.  
     Druid uses double quotes to escape individual field values. For example, a value with a comma returns `"A,B"`.
     If the field value contains a double quote character, Druid escapes it with a second double quote character.
     For example, `foo"bar` becomes `foo""bar`.
      This format includes a single trailing newline character so you can detect a truncated response.

* `header`: Boolean value that determines whether to return information on column names. When set to `true`, Druid returns the column names as the first row of the results. To also get information on the column types, set `typesHeader` or `sqlTypesHeader` to `true`. For a comparative overview of data formats and configurations for the header, see the [Query output format](#query-output-format) table.

* `typesHeader`: Adds Druid runtime type information in the header. Requires `header` to be set to `true`. Complex types, like sketches, will be reported as `COMPLEX<typeName>` if a particular complex type name is known for that field, or as `COMPLEX` if the particular type name is unknown or mixed.

* `sqlTypesHeader`: Adds SQL type information in the header. Requires `header` to be set to `true`.

   For compatibility, Druid returns the HTTP header `X-Druid-SQL-Header-Included: yes` when all of the following conditions are met:
   * The `header` property is set to true.
   * The version of Druid supports `typesHeader` and `sqlTypesHeader`, regardless of whether either property is set.

* `context`: JSON object containing optional [SQL query context parameters](../querying/sql-query-context.md), such as to set the query ID, time zone, and whether to use an approximation algorithm for distinct count. You can also set the context through the SQL SET command. For more information, see [Druid SQL overview](../querying/sql.md#set).

* `parameters`: List of query parameters for parameterized queries. Each parameter in the array should be a JSON object containing the parameter's SQL data type and parameter value. For more information on using dynamic parameters, see [Dynamic parameters](../querying/sql.md#dynamic-parameters). For a list of supported SQL types, see [Data types](../querying/sql-data-types.md).

    For example:

    ```json
    {
        "query": "SELECT \"arrayDouble\", \"stringColumn\" FROM \"array_example\" WHERE ARRAY_CONTAINS(\"arrayDouble\", ?) AND \"stringColumn\" = ?",
        "parameters": [
            {"type": "ARRAY", "value": [999.0, null, 5.5]},
            {"type": "VARCHAR", "value": "bar"}
            ]
    }
    ```

##### Text Format Request body

Druid also allows you to submit SQL queries in text format which is simpler than above JSON format. 
To do this, just set the `Content-Type` request header to `text/plain` or `application/x-www-form-urlencoded`, and pass SQL via the HTTP Body. 

If `application/x-www-form-urlencoded` is used, make sure the SQL query is URL-encoded.

If there are multiple `Content-Type` headers, the **first** one is used.

For response, the `resultFormat` is always `object` with the HTTP response header `Content-Type: application/json`.
If you want more control over the query context or response format, use the above JSON format request body instead.

The following example demonstrates how to submit a SQL query in text format:

```commandline
echo 'SELECT 1' | curl -H 'Content-Type: text/plain' http://ROUTER_IP:ROUTER_PORT/druid/v2/sql --data @- 
```

We can also use `application/x-www-form-urlencoded` to submit URL-encoded SQL queries as shown by the following examples:

```commandline
echo 'SELECT%20%31' | curl http://ROUTER_IP:ROUTER_PORT/druid/v2/sql --data @-
echo 'SELECT 1' | curl http://ROUTER_IP:ROUTER_PORT/druid/v2/sql --data-urlencode @-
```

The `curl` tool uses `application/x-www-form-urlencoded` as Content-Type header if the header is not given.

The first example pass the URL-encoded query `SELECT%20%31`, which is `SELECT 1`, to the `curl` and `curl` will directly sends it to the server.
While the second example passes the raw query `SELECT 1` to `curl` and the `curl` encodes the query to `SELECT%20%31` because of `--data-urlencode` option and sends the encoded text to the server.

#### Responses

<Tabs>

<TabItem value="1" label="200 SUCCESS">


*Successfully submitted query*

</TabItem>
<TabItem value="2" label="400 BAD REQUEST">


*Error thrown due to bad query. Returns a JSON object detailing the error with the following format:*

```json
{
    "error": "A well-defined error code.",
    "errorMessage": "A message with additional details about the error.",
    "errorClass": "Class of exception that caused this error.",
    "host": "The host on which the error occurred."
}
```
</TabItem>
<TabItem value="3" label="500 INTERNAL SERVER ERROR">


*Request not sent due to unexpected conditions. Returns a JSON object detailing the error with the following format:*

```json
{
    "error": "A well-defined error code.",
    "errorMessage": "A message with additional details about the error.",
    "errorClass": "Class of exception that caused this error.",
    "host": "The host on which the error occurred."
}
```

</TabItem>
</Tabs>

#### Client-side error handling and truncated responses

Druid reports errors that occur before the response body is sent as JSON with an HTTP 500 status code. The errors are reported using the same format as [native Druid query errors](../querying/querying.md#query-errors).
If an error occurs while Druid is sending the response body, the server handling the request stops the response midstream and logs an error.

This means that when you call the SQL API, you must properly handle response truncation.
For  `object` and `array` formats, truncated responses are invalid JSON.
For line-oriented formats, Druid includes a newline character as the final character of every complete response. Absence of a final newline character indicates a truncated response.

If you detect a truncated response, treat it as an error.

---

#### Sample request

In the following example, this query demonstrates the following actions:
- Retrieves all rows from the `wikipedia` datasource.
- Filters the results where the `user` value is `BlueMoon2662`.
- Applies the `sqlTimeZone` context parameter to set the time zone of results to `America/Los_Angeles`.
- Returns descriptors for `header`, `typesHeader`, and `sqlTypesHeader`.


<Tabs>

<TabItem value="4" label="cURL">


```shell
curl "http://ROUTER_IP:ROUTER_PORT/druid/v2/sql" \
--header 'Content-Type: application/json' \
--data '{
    "query": "SELECT * FROM wikipedia WHERE user='\''BlueMoon2662'\''",
    "context" : {"sqlTimeZone" : "America/Los_Angeles"},
    "header" : true,
    "typesHeader" : true,
    "sqlTypesHeader" : true
}'
```

</TabItem>
<TabItem value="5" label="HTTP">


```HTTP
POST /druid/v2/sql HTTP/1.1
Host: http://ROUTER_IP:ROUTER_PORT
Content-Type: application/json
Content-Length: 201

{
    "query": "SELECT * FROM wikipedia WHERE user='BlueMoon2662'",
    "context" : {"sqlTimeZone" : "America/Los_Angeles"},
    "header" : true,
    "typesHeader" : true,
    "sqlTypesHeader" : true
}
```

</TabItem>
</Tabs>

You can also specify query-level context parameters directly within the SQL query string using the `SET` command. For more details, see [SET](../querying/sql.md#set).

The following request body is functionally equivalent to the previous example and uses SET instead of the `context` parameter:

```JSON
{
  "query": "SET sqlTimeZone='America/Los_Angeles'; SELECT * FROM wikipedia WHERE user='BlueMoon2662'",
  "header": true,
  "typesHeader": true,
  "sqlTypesHeader": true
}
```


#### Sample response

<details>
  <summary>View the response</summary>

```json
[
    {
        "__time": {
            "type": "LONG",
            "sqlType": "TIMESTAMP"
        },
        "channel": {
            "type": "STRING",
            "sqlType": "VARCHAR"
        },
        "cityName": {
            "type": "STRING",
            "sqlType": "VARCHAR"
        },
        "comment": {
            "type": "STRING",
            "sqlType": "VARCHAR"
        },
        "countryIsoCode": {
            "type": "STRING",
            "sqlType": "VARCHAR"
        },
        "countryName": {
            "type": "STRING",
            "sqlType": "VARCHAR"
        },
        "isAnonymous": {
            "type": "STRING",
            "sqlType": "VARCHAR"
        },
        "isMinor": {
            "type": "STRING",
            "sqlType": "VARCHAR"
        },
        "isNew": {
            "type": "STRING",
            "sqlType": "VARCHAR"
        },
        "isRobot": {
            "type": "STRING",
            "sqlType": "VARCHAR"
        },
        "isUnpatrolled": {
            "type": "STRING",
            "sqlType": "VARCHAR"
        },
        "metroCode": {
            "type": "LONG",
            "sqlType": "BIGINT"
        },
        "namespace": {
            "type": "STRING",
            "sqlType": "VARCHAR"
        },
        "page": {
            "type": "STRING",
            "sqlType": "VARCHAR"
        },
        "regionIsoCode": {
            "type": "STRING",
            "sqlType": "VARCHAR"
        },
        "regionName": {
            "type": "STRING",
            "sqlType": "VARCHAR"
        },
        "user": {
            "type": "STRING",
            "sqlType": "VARCHAR"
        },
        "delta": {
            "type": "LONG",
            "sqlType": "BIGINT"
        },
        "added": {
            "type": "LONG",
            "sqlType": "BIGINT"
        },
        "deleted": {
            "type": "LONG",
            "sqlType": "BIGINT"
        }
    },
    {
        "__time": "2015-09-11T17:47:53.259-07:00",
        "channel": "#ja.wikipedia",
        "cityName": null,
        "comment": "/* 対戦通算成績と得失点 */",
        "countryIsoCode": null,
        "countryName": null,
        "isAnonymous": "false",
        "isMinor": "true",
        "isNew": "false",
        "isRobot": "false",
        "isUnpatrolled": "false",
        "metroCode": null,
        "namespace": "Main",
        "page": "アルビレックス新潟の年度別成績一覧",
        "regionIsoCode": null,
        "regionName": null,
        "user": "BlueMoon2662",
        "delta": 14,
        "added": 14,
        "deleted": 0
    }
]
```
</details>

### Cancel a query

Cancels a query on the Router or the Broker with the associated `sqlQueryId`. The `sqlQueryId` can be manually set when the query is submitted in the query context parameter, or if not set, Druid will generate one and return it in the response header when the query is successfully submitted. Note that Druid does not enforce a unique `sqlQueryId` in the query context. If you've set the same `sqlQueryId` for multiple queries, Druid cancels all requests with that query ID.

When you cancel a query, Druid handles the cancellation in a best-effort manner. Druid immediately marks the query as canceled and aborts the query execution as soon as possible. However, the query may continue running for a short time after you make the cancellation request.

Cancellation requests require READ permission on all resources used in the SQL query.

#### URL

`DELETE` `/druid/v2/sql/{sqlQueryId}`

#### Responses

<Tabs>

<TabItem value="6" label="202 SUCCESS">


*Successfully deleted query*

</TabItem>
<TabItem value="7" label="403 FORBIDDEN">


*Authorization failure*

</TabItem>
<TabItem value="8" label="404 NOT FOUND">


*Invalid `sqlQueryId` or query was completed before cancellation request*

</TabItem>
</Tabs>

---

#### Sample request

The following example cancels a request with the set query ID `request01`.

<Tabs>

<TabItem value="9" label="cURL">


```shell
curl --request DELETE "http://ROUTER_IP:ROUTER_PORT/druid/v2/sql/request01"
```

</TabItem>
<TabItem value="10" label="HTTP">


```HTTP
DELETE /druid/v2/sql/request01 HTTP/1.1
Host: http://ROUTER_IP:ROUTER_PORT
```

</TabItem>
</Tabs>

#### Sample response

A successful response results in an `HTTP 202` message code and an empty response body.

### Query output format

The following table shows examples of how Druid returns the column names and data types based on the result format and the type request.
In all cases, `header` is true.
The examples includes the first row of results, where the value of `user` is `BlueMoon2662`.

```
| Format | typesHeader | sqlTypesHeader | Example output                                                                             |
|--------|-------------|----------------|--------------------------------------------------------------------------------------------|
| object | true        | false          | [ { "user" : { "type" : "STRING" } }, { "user" : "BlueMoon2662" } ]                        |
| object | true        | true           | [ { "user" : { "type" : "STRING", "sqlType" : "VARCHAR" } }, { "user" : "BlueMoon2662" } ] |
| object | false       | true           | [ { "user" : { "sqlType" : "VARCHAR" } }, { "user" : "BlueMoon2662" } ]                    |
| object | false       | false          | [ { "user" : null }, { "user" : "BlueMoon2662" } ]                                         |
| array  | true        | false          | [ [ "user" ], [ "STRING" ], [ "BlueMoon2662" ] ]                                           |
| array  | true        | true           | [ [ "user" ], [ "STRING" ], [ "VARCHAR" ], [ "BlueMoon2662" ] ]                            |
| array  | false       | true           | [ [ "user" ], [ "VARCHAR" ], [ "BlueMoon2662" ] ]                                          |
| array  | false       | false          | [ [ "user" ], [ "BlueMoon2662" ] ]                                                         |
| csv    | true        | false          | user STRING BlueMoon2662                                                                   |
| csv    | true        | true           | user STRING VARCHAR BlueMoon2662                                                           |
| csv    | false       | true           | user VARCHAR BlueMoon2662                                                                  |
| csv    | false       | false          | user BlueMoon2662                                                                          |
```

## Query from deep storage

You can use the `sql/statements` endpoint to query segments that exist only in deep storage and are not loaded onto your Historical processes as determined by your load rules.

Note that at least one segment of a datasource must be available on a Historical process so that the Broker can plan your query. A quick way to check if this is true is whether or not a datasource is visible in the Druid console.


For more information, see [Query from deep storage](../querying/query-from-deep-storage.md).

### Submit a query

Submit a query for data stored in deep storage. Any data ingested into Druid is placed into deep storage. The query is contained in the "query" field in the JSON object within the request payload.

Note that at least part of a datasource must be available on a Historical process so that Druid can plan your query and only the user who submits a query can see the results.

#### URL

`POST` `/druid/v2/sql/statements`

#### Request body

Generally, the `sql` and `sql/statements` endpoints support the same response body fields with minor differences. For general information about the available fields, see [Submit a query to the `sql` endpoint](#submit-a-query).

Keep the following in mind when submitting queries to the `sql/statements` endpoint:

- Apart from the context parameters mentioned [here](../multi-stage-query/reference.md#context-parameters) there are additional context parameters for `sql/statements` specifically:

   - `executionMode`  determines how query results are fetched. Druid currently only supports `ASYNC`. You must manually retrieve your results after the query completes.
   - `selectDestination` determines where final results get written. By default, results are written to task reports. Set this parameter to `durableStorage` to instruct Druid to write the results from SELECT queries to durable storage, which allows you to fetch larger result sets. For result sets with more than 3000 rows, it is highly recommended to use `durableStorage`. Note that this requires you to have [durable storage for MSQ](../operations/durable-storage.md) enabled.

#### Responses

<Tabs>

<TabItem value="1" label="200 SUCCESS">


*Successfully queried from deep storage*

</TabItem>
<TabItem value="2" label="400 BAD REQUEST">


*Error thrown due to bad query. Returns a JSON object detailing the error with the following format:*

```json
{
    "error": "Summary of the encountered error.",
    "errorClass": "Class of exception that caused this error.",
    "host": "The host on which the error occurred.",
    "errorCode": "Well-defined error code.",
    "persona": "Role or persona associated with the error.",
    "category": "Classification of the error.",
    "errorMessage": "Summary of the encountered issue with expanded information.",
    "context": "Additional context about the error."
}
```

</TabItem>
</Tabs>

---

#### Sample request

<Tabs>

<TabItem value="3" label="cURL">


```shell
curl "http://ROUTER_IP:ROUTER_PORT/druid/v2/sql/statements" \
--header 'Content-Type: application/json' \
--data '{
    "query": "SELECT * FROM wikipedia WHERE user='\''BlueMoon2662'\''",
    "context": {
        "executionMode":"ASYNC"
    }
}'
```

</TabItem>
<TabItem value="4" label="HTTP">


```HTTP
POST /druid/v2/sql/statements HTTP/1.1
Host: http://ROUTER_IP:ROUTER_PORT
Content-Type: application/json
Content-Length: 134

{
    "query": "SELECT * FROM wikipedia WHERE user='BlueMoon2662'",
    "context": {
        "executionMode":"ASYNC"
    }
}
```

</TabItem>
</Tabs>

#### Sample response

<details>
  <summary>View the response</summary>

  ```json
{
    "queryId": "query-b82a7049-b94f-41f2-a230-7fef94768745",
    "state": "ACCEPTED",
    "createdAt": "2023-07-26T21:16:25.324Z",
    "schema": [
        {
            "name": "__time",
            "type": "TIMESTAMP",
            "nativeType": "LONG"
        },
        {
            "name": "channel",
            "type": "VARCHAR",
            "nativeType": "STRING"
        },
        {
            "name": "cityName",
            "type": "VARCHAR",
            "nativeType": "STRING"
        },
        {
            "name": "comment",
            "type": "VARCHAR",
            "nativeType": "STRING"
        },
        {
            "name": "countryIsoCode",
            "type": "VARCHAR",
            "nativeType": "STRING"
        },
        {
            "name": "countryName",
            "type": "VARCHAR",
            "nativeType": "STRING"
        },
        {
            "name": "isAnonymous",
            "type": "BIGINT",
            "nativeType": "LONG"
        },
        {
            "name": "isMinor",
            "type": "BIGINT",
            "nativeType": "LONG"
        },
        {
            "name": "isNew",
            "type": "BIGINT",
            "nativeType": "LONG"
        },
        {
            "name": "isRobot",
            "type": "BIGINT",
            "nativeType": "LONG"
        },
        {
            "name": "isUnpatrolled",
            "type": "BIGINT",
            "nativeType": "LONG"
        },
        {
            "name": "metroCode",
            "type": "BIGINT",
            "nativeType": "LONG"
        },
        {
            "name": "namespace",
            "type": "VARCHAR",
            "nativeType": "STRING"
        },
        {
            "name": "page",
            "type": "VARCHAR",
            "nativeType": "STRING"
        },
        {
            "name": "regionIsoCode",
            "type": "VARCHAR",
            "nativeType": "STRING"
        },
        {
            "name": "regionName",
            "type": "VARCHAR",
            "nativeType": "STRING"
        },
        {
            "name": "user",
            "type": "VARCHAR",
            "nativeType": "STRING"
        },
        {
            "name": "delta",
            "type": "BIGINT",
            "nativeType": "LONG"
        },
        {
            "name": "added",
            "type": "BIGINT",
            "nativeType": "LONG"
        },
        {
            "name": "deleted",
            "type": "BIGINT",
            "nativeType": "LONG"
        }
    ],
    "durationMs": -1
}
  ```
</details>

### Get query status

Retrieves information about the query associated with the given query ID. The response matches the response from the POST API if the query is accepted or running and the execution mode is  `ASYNC`. In addition to the fields that this endpoint shares with `POST /sql/statements`, a completed query's status includes the following:

- A `result` object that summarizes information about your results, such as the total number of rows and sample records.
- A `pages` object that includes the following information for each page of results:
  -  `numRows`: the number of rows in that page of results.
  - `sizeInBytes`: the size of the page.
  - `id`: the page number that you can use to reference a specific page when you get query results.

If the optional query parameter `detail` is supplied, then the response also includes the following:
- A `stages` object that summarizes information about the different stages being used for query execution, such as stage number, phase, start time, duration, input and output information, processing methods, and partitioning.
- A `counters` object that provides details on the rows, bytes, and files processed at various stages for each worker across different channels, along with sort progress.
- A `warnings` object that provides details about any warnings.

#### URL

`GET` `/druid/v2/sql/statements/{queryId}`

#### Query parameters
* `detail` (optional)
    * Type: Boolean
    * Default: false
    * Fetch additional details about the query, which includes the information about different stages, counters for each stage, and any warnings.

#### Responses

<Tabs>

<TabItem value="5" label="200 SUCCESS">


*Successfully retrieved query status*

</TabItem>
<TabItem value="6" label="400 BAD REQUEST">


*Error thrown due to bad query. Returns a JSON object detailing the error with the following format:*

```json
{
    "error": "Summary of the encountered error.",
    "errorCode": "Well-defined error code.",
    "persona": "Role or persona associated with the error.",
    "category": "Classification of the error.",
    "errorMessage": "Summary of the encountered issue with expanded information.",
    "context": "Additional context about the error."
}
```

</TabItem>
</Tabs>

#### Sample request

The following example retrieves the status of a query with specified ID `query-9b93f6f7-ab0e-48f5-986a-3520f84f0804`.

<Tabs>

<TabItem value="7" label="cURL">


```shell
curl "http://ROUTER_IP:ROUTER_PORT/druid/v2/sql/statements/query-9b93f6f7-ab0e-48f5-986a-3520f84f0804?detail=true"
```

</TabItem>
<TabItem value="8" label="HTTP">


```HTTP
GET /druid/v2/sql/statements/query-9b93f6f7-ab0e-48f5-986a-3520f84f0804?detail=true HTTP/1.1
Host: http://ROUTER_IP:ROUTER_PORT
```

</TabItem>
</Tabs>

#### Sample response

<details>
  <summary>View the response</summary>

  ```json
{
    "queryId": "query-9b93f6f7-ab0e-48f5-986a-3520f84f0804",
    "state": "SUCCESS",
    "createdAt": "2023-07-26T22:57:46.620Z",
    "schema": [
        {
            "name": "__time",
            "type": "TIMESTAMP",
            "nativeType": "LONG"
        },
        {
            "name": "channel",
            "type": "VARCHAR",
            "nativeType": "STRING"
        },
        {
            "name": "cityName",
            "type": "VARCHAR",
            "nativeType": "STRING"
        },
        {
            "name": "comment",
            "type": "VARCHAR",
            "nativeType": "STRING"
        },
        {
            "name": "countryIsoCode",
            "type": "VARCHAR",
            "nativeType": "STRING"
        },
        {
            "name": "countryName",
            "type": "VARCHAR",
            "nativeType": "STRING"
        },
        {
            "name": "isAnonymous",
            "type": "BIGINT",
            "nativeType": "LONG"
        },
        {
            "name": "isMinor",
            "type": "BIGINT",
            "nativeType": "LONG"
        },
        {
            "name": "isNew",
            "type": "BIGINT",
            "nativeType": "LONG"
        },
        {
            "name": "isRobot",
            "type": "BIGINT",
            "nativeType": "LONG"
        },
        {
            "name": "isUnpatrolled",
            "type": "BIGINT",
            "nativeType": "LONG"
        },
        {
            "name": "metroCode",
            "type": "BIGINT",
            "nativeType": "LONG"
        },
        {
            "name": "namespace",
            "type": "VARCHAR",
            "nativeType": "STRING"
        },
        {
            "name": "page",
            "type": "VARCHAR",
            "nativeType": "STRING"
        },
        {
            "name": "regionIsoCode",
            "type": "VARCHAR",
            "nativeType": "STRING"
        },
        {
            "name": "regionName",
            "type": "VARCHAR",
            "nativeType": "STRING"
        },
        {
            "name": "user",
            "type": "VARCHAR",
            "nativeType": "STRING"
        },
        {
            "name": "delta",
            "type": "BIGINT",
            "nativeType": "LONG"
        },
        {
            "name": "added",
            "type": "BIGINT",
            "nativeType": "LONG"
        },
        {
            "name": "deleted",
            "type": "BIGINT",
            "nativeType": "LONG"
        }
    ],
    "durationMs": 25591,
    "result": {
        "numTotalRows": 1,
        "totalSizeInBytes": 375,
        "dataSource": "__query_select",
        "sampleRecords": [
            [
                1442018873259,
                "#ja.wikipedia",
                "",
                "/* 対戦通算成績と得失点 */",
                "",
                "",
                0,
                1,
                0,
                0,
                0,
                0,
                "Main",
                "アルビレックス新潟の年度別成績一覧",
                "",
                "",
                "BlueMoon2662",
                14,
                14,
                0
            ]
        ],
        "pages": [
            {
                "id": 0,
                "numRows": 1,
                "sizeInBytes": 375
            }
        ]
    },
    "stages": [
        {
            "stageNumber": 0,
            "definition": {
                "id": "query-9b93f6f7-ab0e-48f5-986a-3520f84f0804_0",
                "input": [
                    {
                        "type": "table",
                        "dataSource": "wikipedia",
                        "intervals": [
                            "-146136543-09-08T08:23:32.096Z/146140482-04-24T15:36:27.903Z"
                        ],
                        "filter": {
                            "type": "equals",
                            "column": "user",
                            "matchValueType": "STRING",
                            "matchValue": "BlueMoon2662"
                        },
                        "filterFields": [
                            "user"
                        ]
                    }
                ],
                "processor": {
                    "type": "scan",
                    "query": {
                        "queryType": "scan",
                        "dataSource": {
                            "type": "inputNumber",
                            "inputNumber": 0
                        },
                        "intervals": {
                            "type": "intervals",
                            "intervals": [
                                "-146136543-09-08T08:23:32.096Z/146140482-04-24T15:36:27.903Z"
                            ]
                        },
                        "virtualColumns": [
                            {
                                "type": "expression",
                                "name": "v0",
                                "expression": "'BlueMoon2662'",
                                "outputType": "STRING"
                            }
                        ],
                        "resultFormat": "compactedList",
                        "limit": 1001,
                        "filter": {
                            "type": "equals",
                            "column": "user",
                            "matchValueType": "STRING",
                            "matchValue": "BlueMoon2662"
                        },
                        "columns": [
                            "__time",
                            "added",
                            "channel",
                            "cityName",
                            "comment",
                            "commentLength",
                            "countryIsoCode",
                            "countryName",
                            "deleted",
                            "delta",
                            "deltaBucket",
                            "diffUrl",
                            "flags",
                            "isAnonymous",
                            "isMinor",
                            "isNew",
                            "isRobot",
                            "isUnpatrolled",
                            "metroCode",
                            "namespace",
                            "page",
                            "regionIsoCode",
                            "regionName",
                            "v0"
                        ],
                        "context": {
                            "__resultFormat": "array",
                            "__user": "allowAll",
                            "executionMode": "async",
                            "finalize": true,
                            "maxNumTasks": 2,
                            "maxParseExceptions": 0,
                            "queryId": "33b53acb-7533-4880-a81b-51c16c489eab",
                            "scanSignature": "[{\"name\":\"__time\",\"type\":\"LONG\"},{\"name\":\"added\",\"type\":\"LONG\"},{\"name\":\"channel\",\"type\":\"STRING\"},{\"name\":\"cityName\",\"type\":\"STRING\"},{\"name\":\"comment\",\"type\":\"STRING\"},{\"name\":\"commentLength\",\"type\":\"LONG\"},{\"name\":\"countryIsoCode\",\"type\":\"STRING\"},{\"name\":\"countryName\",\"type\":\"STRING\"},{\"name\":\"deleted\",\"type\":\"LONG\"},{\"name\":\"delta\",\"type\":\"LONG\"},{\"name\":\"deltaBucket\",\"type\":\"LONG\"},{\"name\":\"diffUrl\",\"type\":\"STRING\"},{\"name\":\"flags\",\"type\":\"STRING\"},{\"name\":\"isAnonymous\",\"type\":\"STRING\"},{\"name\":\"isMinor\",\"type\":\"STRING\"},{\"name\":\"isNew\",\"type\":\"STRING\"},{\"name\":\"isRobot\",\"type\":\"STRING\"},{\"name\":\"isUnpatrolled\",\"type\":\"STRING\"},{\"name\":\"metroCode\",\"type\":\"STRING\"},{\"name\":\"namespace\",\"type\":\"STRING\"},{\"name\":\"page\",\"type\":\"STRING\"},{\"name\":\"regionIsoCode\",\"type\":\"STRING\"},{\"name\":\"regionName\",\"type\":\"STRING\"},{\"name\":\"v0\",\"type\":\"STRING\"}]",
                            "sqlOuterLimit": 1001,
                            "sqlQueryId": "33b53acb-7533-4880-a81b-51c16c489eab",
                            "sqlStringifyArrays": false
                        },
                        "columnTypes": [
                            "LONG",
                            "LONG",
                            "STRING",
                            "STRING",
                            "STRING",
                            "LONG",
                            "STRING",
                            "STRING",
                            "LONG",
                            "LONG",
                            "LONG",
                            "STRING",
                            "STRING",
                            "STRING",
                            "STRING",
                            "STRING",
                            "STRING",
                            "STRING",
                            "STRING",
                            "STRING",
                            "STRING",
                            "STRING",
                            "STRING",
                            "STRING"
                        ],
                        "granularity": {
                            "type": "all"
                        },
                        "legacy": false
                    }
                },
                "signature": [
                    {
                        "name": "__boost",
                        "type": "LONG"
                    },
                    {
                        "name": "__time",
                        "type": "LONG"
                    },
                    {
                        "name": "added",
                        "type": "LONG"
                    },
                    {
                        "name": "channel",
                        "type": "STRING"
                    },
                    {
                        "name": "cityName",
                        "type": "STRING"
                    },
                    {
                        "name": "comment",
                        "type": "STRING"
                    },
                    {
                        "name": "commentLength",
                        "type": "LONG"
                    },
                    {
                        "name": "countryIsoCode",
                        "type": "STRING"
                    },
                    {
                        "name": "countryName",
                        "type": "STRING"
                    },
                    {
                        "name": "deleted",
                        "type": "LONG"
                    },
                    {
                        "name": "delta",
                        "type": "LONG"
                    },
                    {
                        "name": "deltaBucket",
                        "type": "LONG"
                    },
                    {
                        "name": "diffUrl",
                        "type": "STRING"
                    },
                    {
                        "name": "flags",
                        "type": "STRING"
                    },
                    {
                        "name": "isAnonymous",
                        "type": "STRING"
                    },
                    {
                        "name": "isMinor",
                        "type": "STRING"
                    },
                    {
                        "name": "isNew",
                        "type": "STRING"
                    },
                    {
                        "name": "isRobot",
                        "type": "STRING"
                    },
                    {
                        "name": "isUnpatrolled",
                        "type": "STRING"
                    },
                    {
                        "name": "metroCode",
                        "type": "STRING"
                    },
                    {
                        "name": "namespace",
                        "type": "STRING"
                    },
                    {
                        "name": "page",
                        "type": "STRING"
                    },
                    {
                        "name": "regionIsoCode",
                        "type": "STRING"
                    },
                    {
                        "name": "regionName",
                        "type": "STRING"
                    },
                    {
                        "name": "v0",
                        "type": "STRING"
                    }
                ],
                "shuffleSpec": {
                    "type": "mix"
                },
                "maxWorkerCount": 1
            },
            "phase": "FINISHED",
            "workerCount": 1,
            "partitionCount": 1,
            "shuffle": "mix",
            "output": "localStorage",
            "startTime": "2024-07-31T15:20:21.255Z",
            "duration": 103
        },
        {
            "stageNumber": 1,
            "definition": {
                "id": "query-9b93f6f7-ab0e-48f5-986a-3520f84f0804_1",
                "input": [
                    {
                        "type": "stage",
                        "stage": 0
                    }
                ],
                "processor": {
                    "type": "limit",
                    "limit": 1001
                },
                "signature": [
                    {
                        "name": "__boost",
                        "type": "LONG"
                    },
                    {
                        "name": "__time",
                        "type": "LONG"
                    },
                    {
                        "name": "added",
                        "type": "LONG"
                    },
                    {
                        "name": "channel",
                        "type": "STRING"
                    },
                    {
                        "name": "cityName",
                        "type": "STRING"
                    },
                    {
                        "name": "comment",
                        "type": "STRING"
                    },
                    {
                        "name": "commentLength",
                        "type": "LONG"
                    },
                    {
                        "name": "countryIsoCode",
                        "type": "STRING"
                    },
                    {
                        "name": "countryName",
                        "type": "STRING"
                    },
                    {
                        "name": "deleted",
                        "type": "LONG"
                    },
                    {
                        "name": "delta",
                        "type": "LONG"
                    },
                    {
                        "name": "deltaBucket",
                        "type": "LONG"
                    },
                    {
                        "name": "diffUrl",
                        "type": "STRING"
                    },
                    {
                        "name": "flags",
                        "type": "STRING"
                    },
                    {
                        "name": "isAnonymous",
                        "type": "STRING"
                    },
                    {
                        "name": "isMinor",
                        "type": "STRING"
                    },
                    {
                        "name": "isNew",
                        "type": "STRING"
                    },
                    {
                        "name": "isRobot",
                        "type": "STRING"
                    },
                    {
                        "name": "isUnpatrolled",
                        "type": "STRING"
                    },
                    {
                        "name": "metroCode",
                        "type": "STRING"
                    },
                    {
                        "name": "namespace",
                        "type": "STRING"
                    },
                    {
                        "name": "page",
                        "type": "STRING"
                    },
                    {
                        "name": "regionIsoCode",
                        "type": "STRING"
                    },
                    {
                        "name": "regionName",
                        "type": "STRING"
                    },
                    {
                        "name": "v0",
                        "type": "STRING"
                    }
                ],
                "shuffleSpec": {
                    "type": "maxCount",
                    "clusterBy": {
                        "columns": [
                            {
                                "columnName": "__boost",
                                "order": "ASCENDING"
                            }
                        ]
                    },
                    "partitions": 1
                },
                "maxWorkerCount": 1
            },
            "phase": "FINISHED",
            "workerCount": 1,
            "partitionCount": 1,
            "shuffle": "globalSort",
            "output": "localStorage",
            "startTime": "2024-07-31T15:20:21.355Z",
            "duration": 10,
            "sort": true
        }
    ],
    "counters": {
        "0": {
            "0": {
                "input0": {
                    "type": "channel",
                    "rows": [
                        24433
                    ],
                    "bytes": [
                        7393933
                    ],
                    "files": [
                        22
                    ],
                    "totalFiles": [
                        22
                    ]
                }
            }
        },
        "1": {
            "0": {
                "sortProgress": {
                    "type": "sortProgress",
                    "totalMergingLevels": -1,
                    "levelToTotalBatches": {},
                    "levelToMergedBatches": {},
                    "totalMergersForUltimateLevel": -1,
                    "triviallyComplete": true,
                    "progressDigest": 1
                }
            }
        }
    },
    "warnings": []
}
  ```
</details>


### Get query results

Retrieves results for completed queries. Results are separated into pages, so you can use the optional `page` parameter to refine the results you get. Druid returns information about the composition of each page and its page number (`id`). For information about pages, see [Get query status](#get-query-status).

If a page number isn't passed, all results are returned sequentially in the same response. If you have large result sets, you may encounter timeouts based on the value configured for `druid.router.http.readTimeout`.

Getting the query results for an ingestion query returns an empty response.

#### URL

`GET` `/druid/v2/sql/statements/{queryId}/results`

#### Query parameters
* `page` (optional)
    * Type: Int
    * Fetch results based on page numbers. If not specified, all results are returned sequentially starting from page 0 to N in the same response.
* `resultFormat` (optional)
    * Type: String
    * Defines the format in which the results are presented. The following options are supported `arrayLines`,`objectLines`,`array`,`object`, and `csv`. The default is `object`.
* `filename` (optional)
    * Type: String  
    * If set, attaches a `Content-Disposition` header to the response with the value of `attachment; filename={filename}`. The filename must not be longer than 255 characters and must not contain the characters `/`, `\`, `:`, `*`, `?`, `"`, `<`, `>`, `|`, `\0`, `\n`, or `\r`.

#### Responses

<Tabs>

<TabItem value="9" label="200 SUCCESS">


*Successfully retrieved query results*

</TabItem>
<TabItem value="10" label="400 BAD REQUEST">


*Query in progress. Returns a JSON object detailing the error with the following format:*

```json
{
    "error": "Summary of the encountered error.",
    "errorCode": "Well-defined error code.",
    "persona": "Role or persona associated with the error.",
    "category": "Classification of the error.",
    "errorMessage": "Summary of the encountered issue with expanded information.",
    "context": "Additional context about the error."
}
```

</TabItem>
<TabItem value="11" label="404 NOT FOUND">


*Query not found, failed or canceled*

</TabItem>
<TabItem value="12" label="500 SERVER ERROR">


*Error thrown due to bad query. Returns a JSON object detailing the error with the following format:*

```json
{
    "error": "Summary of the encountered error.",
    "errorCode": "Well-defined error code.",
    "persona": "Role or persona associated with the error.",
    "category": "Classification of the error.",
    "errorMessage": "Summary of the encountered issue with expanded information.",
    "context": "Additional context about the error."
}
```

</TabItem>
</Tabs>

---

#### Sample request

The following example retrieves the status of a query with specified ID `query-f3bca219-173d-44d4-bdc7-5002e910352f`.

<Tabs>

<TabItem value="13" label="cURL">


```shell
curl "http://ROUTER_IP:ROUTER_PORT/druid/v2/sql/statements/query-f3bca219-173d-44d4-bdc7-5002e910352f/results"
```

</TabItem>
<TabItem value="14" label="HTTP">


```HTTP
GET /druid/v2/sql/statements/query-f3bca219-173d-44d4-bdc7-5002e910352f/results HTTP/1.1
Host: http://ROUTER_IP:ROUTER_PORT
```

</TabItem>
</Tabs>

#### Sample response

<details>
  <summary>View the response</summary>

  ```json
[
    {
        "__time": 1442018818771,
        "channel": "#en.wikipedia",
        "cityName": "",
        "comment": "added project",
        "countryIsoCode": "",
        "countryName": "",
        "isAnonymous": 0,
        "isMinor": 0,
        "isNew": 0,
        "isRobot": 0,
        "isUnpatrolled": 0,
        "metroCode": 0,
        "namespace": "Talk",
        "page": "Talk:Oswald Tilghman",
        "regionIsoCode": "",
        "regionName": "",
        "user": "GELongstreet",
        "delta": 36,
        "added": 36,
        "deleted": 0
    },
    {
        "__time": 1442018820496,
        "channel": "#ca.wikipedia",
        "cityName": "",
        "comment": "Robot inserta {{Commonscat}} que enllaça amb [[commons:category:Rallicula]]",
        "countryIsoCode": "",
        "countryName": "",
        "isAnonymous": 0,
        "isMinor": 1,
        "isNew": 0,
        "isRobot": 1,
        "isUnpatrolled": 0,
        "metroCode": 0,
        "namespace": "Main",
        "page": "Rallicula",
        "regionIsoCode": "",
        "regionName": "",
        "user": "PereBot",
        "delta": 17,
        "added": 17,
        "deleted": 0
    },
    {
        "__time": 1442018825474,
        "channel": "#en.wikipedia",
        "cityName": "Auburn",
        "comment": "/* Status of peremptory norms under international law */ fixed spelling of 'Wimbledon'",
        "countryIsoCode": "AU",
        "countryName": "Australia",
        "isAnonymous": 1,
        "isMinor": 0,
        "isNew": 0,
        "isRobot": 0,
        "isUnpatrolled": 0,
        "metroCode": 0,
        "namespace": "Main",
        "page": "Peremptory norm",
        "regionIsoCode": "NSW",
        "regionName": "New South Wales",
        "user": "60.225.66.142",
        "delta": 0,
        "added": 0,
        "deleted": 0
    },
    {
        "__time": 1442018828770,
        "channel": "#vi.wikipedia",
        "cityName": "",
        "comment": "fix Lỗi CS1: ngày tháng",
        "countryIsoCode": "",
        "countryName": "",
        "isAnonymous": 0,
        "isMinor": 1,
        "isNew": 0,
        "isRobot": 1,
        "isUnpatrolled": 0,
        "metroCode": 0,
        "namespace": "Main",
        "page": "Apamea abruzzorum",
        "regionIsoCode": "",
        "regionName": "",
        "user": "Cheers!-bot",
        "delta": 18,
        "added": 18,
        "deleted": 0
    },
    {
        "__time": 1442018831862,
        "channel": "#vi.wikipedia",
        "cityName": "",
        "comment": "clean up using [[Project:AWB|AWB]]",
        "countryIsoCode": "",
        "countryName": "",
        "isAnonymous": 0,
        "isMinor": 0,
        "isNew": 0,
        "isRobot": 1,
        "isUnpatrolled": 0,
        "metroCode": 0,
        "namespace": "Main",
        "page": "Atractus flammigerus",
        "regionIsoCode": "",
        "regionName": "",
        "user": "ThitxongkhoiAWB",
        "delta": 18,
        "added": 18,
        "deleted": 0
    },
    {
        "__time": 1442018833987,
        "channel": "#vi.wikipedia",
        "cityName": "",
        "comment": "clean up using [[Project:AWB|AWB]]",
        "countryIsoCode": "",
        "countryName": "",
        "isAnonymous": 0,
        "isMinor": 0,
        "isNew": 0,
        "isRobot": 1,
        "isUnpatrolled": 0,
        "metroCode": 0,
        "namespace": "Main",
        "page": "Agama mossambica",
        "regionIsoCode": "",
        "regionName": "",
        "user": "ThitxongkhoiAWB",
        "delta": 18,
        "added": 18,
        "deleted": 0
    },
    {
        "__time": 1442018837009,
        "channel": "#ca.wikipedia",
        "cityName": "",
        "comment": "/* Imperi Austrohongarès */",
        "countryIsoCode": "",
        "countryName": "",
        "isAnonymous": 0,
        "isMinor": 0,
        "isNew": 0,
        "isRobot": 0,
        "isUnpatrolled": 0,
        "metroCode": 0,
        "namespace": "Main",
        "page": "Campanya dels Balcans (1914-1918)",
        "regionIsoCode": "",
        "regionName": "",
        "user": "Jaumellecha",
        "delta": -20,
        "added": 0,
        "deleted": 20
    },
    {
        "__time": 1442018839591,
        "channel": "#en.wikipedia",
        "cityName": "",
        "comment": "adding comment on notability and possible COI",
        "countryIsoCode": "",
        "countryName": "",
        "isAnonymous": 0,
        "isMinor": 0,
        "isNew": 1,
        "isRobot": 0,
        "isUnpatrolled": 1,
        "metroCode": 0,
        "namespace": "Talk",
        "page": "Talk:Dani Ploeger",
        "regionIsoCode": "",
        "regionName": "",
        "user": "New Media Theorist",
        "delta": 345,
        "added": 345,
        "deleted": 0
    },
    {
        "__time": 1442018841578,
        "channel": "#en.wikipedia",
        "cityName": "",
        "comment": "Copying assessment table to wiki",
        "countryIsoCode": "",
        "countryName": "",
        "isAnonymous": 0,
        "isMinor": 0,
        "isNew": 0,
        "isRobot": 1,
        "isUnpatrolled": 0,
        "metroCode": 0,
        "namespace": "User",
        "page": "User:WP 1.0 bot/Tables/Project/Pubs",
        "regionIsoCode": "",
        "regionName": "",
        "user": "WP 1.0 bot",
        "delta": 121,
        "added": 121,
        "deleted": 0
    },
    {
        "__time": 1442018845821,
        "channel": "#vi.wikipedia",
        "cityName": "",
        "comment": "clean up using [[Project:AWB|AWB]]",
        "countryIsoCode": "",
        "countryName": "",
        "isAnonymous": 0,
        "isMinor": 0,
        "isNew": 0,
        "isRobot": 1,
        "isUnpatrolled": 0,
        "metroCode": 0,
        "namespace": "Main",
        "page": "Agama persimilis",
        "regionIsoCode": "",
        "regionName": "",
        "user": "ThitxongkhoiAWB",
        "delta": 18,
        "added": 18,
        "deleted": 0
    }
]
  ```
</details>

### Cancel a query

Cancels a running or accepted query.

#### URL

`DELETE` `/druid/v2/sql/statements/{queryId}`

#### Responses

<Tabs>

<TabItem value="15" label="200 OK">


*A no op operation since the query is not in a state to be cancelled*

</TabItem>
<TabItem value="16" label="202 ACCEPTED">


*Successfully accepted query for cancellation*

</TabItem>
<TabItem value="17" label="404 SERVER ERROR">


*Invalid query ID. Returns a JSON object detailing the error with the following format:*

```json
{
    "error": "Summary of the encountered error.",
    "errorCode": "Well-defined error code.",
    "persona": "Role or persona associated with the error.",
    "category": "Classification of the error.",
    "errorMessage": "Summary of the encountered issue with expanded information.",
    "context": "Additional context about the error."
}
```

</TabItem>
</Tabs>

---

#### Sample request

The following example cancels a query with specified ID `query-945c9633-2fa2-49ab-80ae-8221c38c024da`.

<Tabs>

<TabItem value="18" label="cURL">


```shell
curl --request DELETE "http://ROUTER_IP:ROUTER_PORT/druid/v2/sql/statements/query-945c9633-2fa2-49ab-80ae-8221c38c024da"
```

</TabItem>
<TabItem value="19" label="HTTP">


```HTTP
DELETE /druid/v2/sql/statements/query-945c9633-2fa2-49ab-80ae-8221c38c024da HTTP/1.1
Host: http://ROUTER_IP:ROUTER_PORT
```

</TabItem>
</Tabs>

#### Sample response

A successful request returns an HTTP `202 ACCEPTED` message code and an empty response body.
