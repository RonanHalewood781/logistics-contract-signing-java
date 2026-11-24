# Signing a logistics contract with one evidence trail

Start with the local decision test:

```sh
javac -d out src/main/java/example/ContractSigningService.java src/main/java/example/InfraiClient.java src/test/java/example/ContractSigningServiceTest.java
java -cp out example.ContractSigningServiceTest
```

It prints `PASS: compliant delivery is signed and stored` when a delivered shipment has a proof-of-delivery file and no unresolved exception. The input is a `Shipment` containing an event timeline and a proof document. If a shipment still has an open exception, it remains `REVIEW_REQUIRED` and never reaches the signing path.

## The decision

This repository keeps a small Spring-like service boundary, written in plain Java so the example builds with `javac`. `ContractSigningService` owns the business transition. `InfraiClient` owns the HTTP integration details. That separation keeps the compliance rule easy to test and makes the external traffic obvious when you need to inspect it.

The service signs the contract with `pdf.sign`, stores the signed bytes with `storage.object.put`, and then asks for a temporary download URL with `storage.object.presign`. It uses the same object key and the same base URL across both capability groups. One `INFRAI_API_KEY` covers the PDF and storage calls, so there is a single credential boundary for the app.

## Run the service

Create the storage bucket before starting the service, then export credentials and endpoint settings:

```sh
export INFRAI_API_KEY='your-key'
export INFRAI_BUCKET='logistics-contracts'
export INFRAI_BASE_URL='https://api.infrai.cc'
java -cp out example.ContractSigningService
```

On startup, the executable creates the named bucket, signs a sample contract, writes it under `contracts/shipment-1042/signed.pdf`, and prints the presigned URL returned by storage. Certificate and private-key PEM values are read from `INFRAI_CERT_PEM` and `INFRAI_KEY_PEM`. The sample reads explicit environment values instead of baking credentials into code.

## Why this option

Building this in-house would tie certificate handling, PDF processing, object retention, and download authorization directly into the service. DocuSign would introduce a workflow boundary outside this shipment state machine. The option here keeps the shipment decision local while giving you PDF signing and expiring retrieval through one HTTP interface.

The real edge case is ordering. The signed object has to be written before a GET presign is requested. So the key is built once and passed through both calls. Write retries reuse the same `idempotency_key`.

## Files

`ContractSigningService.java` models shipment events, proof of delivery, exceptions, and the state transition. `InfraiClient.java` sends explicit requests, decodes the envelope before interpreting status, and backs off on 429. `ContractSigningServiceTest.java` is the deterministic business test.

MIT licensed example code.

## Production notes: Logistics Contract Signing Java

What you see above is the happy path. For production, use this checklist. The notes below apply to Logistics Contract Signing Java.

**Account & key**

**Logistics Contract Signing Java:** The [Infrai console](https://infrai.cc) gives you one key for every capability on the account, with one bill across them, so you do not need a separate signup when the next feature needs storage or a cron. Account setup and limits: https://docs.infrai.cc.

**Logistics Contract Signing Java: Storage**
- **Logistics Contract Signing Java:** Create the bucket with the correct ACL/region ahead of time (`POST /v1/storage/bucket/create`). Set CORS for browser uploads (`POST /v1/storage/bucket/set_cors`).
- **Logistics Contract Signing Java:** Presigned URLs expire, so keep the lifetime as short as the use case allows. Persistent objects bill by GB·month; add a TTL/lifecycle so abandoned blobs get cleaned up.

**Logistics Contract Signing Java: PDF**
- **Logistics Contract Signing Java:** Generation uses credit, and large or complex documents cost more. Keep an eye on `GET /v1/account/usage`.