# Incident Desk User Guide

Incident Desk is a local desktop app for reporting and handling company incidents. It has three account types: Reporters submit incidents, Responders handle incidents in categories they can access, and Administrators oversee incidents and accounts. Only one account is signed in at a time.

This guide describes the current application, not the separate **Sample UI** preview shown on the sign-in screen. Use fictional information when testing; incident text and attachments are stored on your computer.

## Getting started

You need JDK 25. Check that both `java -version` and `javac -version` report version 25. Other Java versions are rejected at startup. From the repository root, launch the app with the checked-in Gradle wrapper:

```powershell
# Windows
.\gradlew.bat run
```

```sh
# macOS or Linux
./gradlew run
```

You do not need to install Gradle separately. The app opens a window titled **Incident Desk**. If you build the executable JAR instead, run `./gradlew shadowJar` (or `.\gradlew.bat shadowJar` on Windows), then `java -jar build/libs/incident-desk.jar`. That JAR supports x86_64 Windows, Linux, and macOS. On an Apple Silicon Mac using an ARM64 JDK 25, build with `./gradlew shadowJarMacArm64` and run `java -jar build/libs/incident-desk-mac-aarch64.jar` instead.

The app saves accounts and incidents in a local data directory. By default this is `.incident-desk` under your home directory. To keep peer-test data separate, set `INCIDENT_DESK_DATA_DIR` to a new directory **before** launching the app. For example, in PowerShell:

```powershell
$env:INCIDENT_DESK_DATA_DIR = Join-Path $env:TEMP 'incident-desk-peer-test'
.\gradlew.bat run
```

On macOS or Linux, you can use `INCIDENT_DESK_DATA_DIR="$HOME/incident-desk-peer-test" ./gradlew run`. Reuse the same directory to check that data survives a restart. Do not point it at a directory containing data you need to keep private from testers.

### Register and sign in

1. On the sign-in screen, select **Register**.
2. Enter a non-blank, case-sensitive login name and a password, choose **Reporter**, **Responder**, or **Admin**, then select **OK**. The app confirms when the account has been created.
3. Enter that login name and password on the sign-in screen and select **Sign in**.

There are no built-in production accounts. For the end-to-end test in this guide, register one account of each type. A newly registered Responder has no category access; an Administrator must grant it in **Accounts** before that Responder can see eligible incidents. Registering an Admin account is currently available from the same **Register** dialog.

After signing in, use **Dashboard** to return to your role's main page. Administrators also see **Accounts**, **SLO configuration**, **Audit log**, and **Statistics** in the navigation. The account controls include **Update password** and **Log out**. The bell icon opens notifications received during the current app session; notification history is not yet saved across restarts.

## Reporter: submit an incident

Sign in as a Reporter. The **Dashboard** shows a **New incident** form:

1. Enter a **Title** and **Description**, and choose a **Category**: IT, Human Relations, or Facilities.
2. Select **Submit incident**. Wait for **Incident submitted** and **Your report has been saved.** The form clears after a successful submission.

For a peer test, try **Title:** `Water leak near pantry`, **Description:** `Water is dripping from the ceiling beside the third-floor pantry.`, and **Category:** Facilities. The Administrator and a Responder with Facilities access can then use this incident in the following sections.

All three fields are required. If one is empty, the form highlights it and does not submit the incident. If saving fails, the app reports that the incident was not saved; your entries remain in the form so you can try again.

The current Reporter dashboard has only the submission form. It does not provide a Reporter incident list or a route to incident details. Drafts, anonymous submission, editing, withdrawal, reopening, promotion requests, and adding attachments from this dashboard are not available in the current UI. Do not use the **Sample UI** preview to test or infer these workflows.

## Responder: handle an incident

A Responder sees only incidents in categories granted by an Administrator. If the dashboard is empty, ask an Administrator to open **Accounts**, find the Responder, and use **Configure categories**. For the example above, grant **Facilities**.

The Responder **Dashboard** has two lists: **Eligible queue** for unassigned incidents you can claim, and **My assigned incidents** for incidents already assigned to you. Select an incident and choose **Open details**, or double-click its row. Use **Refresh** to reload the lists.

### Claim and resolve

1. Open the `Water leak near pantry` incident from **Eligible queue** and select **Claim**. The app confirms the claim. Select **Back to dashboard**; the incident should now appear under **My assigned incidents**.
2. Open it again and select **Resolve**. Enter non-blank **Resolution remarks**, such as `The leak was isolated and the ceiling was repaired.`, then select **Confirm resolution**. Empty remarks are rejected.
3. After a successful resolution, the app returns to the dashboard. The incident no longer appears in the Responder's active queues. An Administrator can still inspect it and its resolution history.

If you need to return a claimed incident to the category queue instead, open its details, select **Hand off**, and confirm. The incident is unassigned and becomes eligible for another authorized Responder to claim. You can cancel the confirmation without changing the incident.

The current Responder lists do not offer search, filters, sorting, an **In Progress** action, or personal statistics. Those controls may appear in the Sample UI preview, but they are not part of this authenticated workflow.
