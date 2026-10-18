# CoffeeAdminApp

Admin app of the [BrewPass](https://github.com/vlszabolcs/brewpass) coffee machine controller.

## Database (Firebase RTDB)

The structure follows the BrewPass firmware:

```
/users/<UID>                 card UID (upper-case hex)
    credit : 200             current credit (Ft)
    name   : "TestElek"
    loan   : 0
    log/<epoch> : 350        credit AFTER the change (purchase and admin change alike)
/Log/<epoch> : {"e": "boot", "d": "..."}   device status log (boot, wifi, config, reader, error, ota)
/conf                        price, free (watched by the machine), coffeeBeans, coffeeThreshold
/stat/free/<YYYYMMDD> : 37   daily number of free-mode purchases (written by the machine)
/stat/income                 total top-ups (this app only)
```

- Every credit change made by the app writes `credit` and `log/<epoch>` together in one transaction,
  so the type of an entry can be derived from the previous one (decrease = purchase, increase = top-up).
- Log keys are epoch seconds and strictly increasing per user.
- The statistics are computed from a local Room cache of `users/*/log` plus `/stat/free`.
- `/conf` is only patched field by field, never overwritten.

## User app (`userapp`)

A separate app for coffee drinkers, built from the same repository: `./gradlew :userapp:installDebug`.

- The phone reads the coffee card by NFC once and binds it to itself: `users/<UID>/owner` = the phone's anonymous auth uid. From then on only that phone can read the card and buy with it. Reading a new card binds the new one and unbinds the old one. A card that is unknown or bound to another phone gets "ask the administrator"; the admin app's credit dialog has "Release phone" for a lost phone or a reinstalled app.
- The app never registers a user or changes credit itself.
- **Átutalás (credit transfer):** amount in whole hundreds, confirm, then the recipient taps their card on the phone. The app sends `remote/<dev>/req/<id> = {cardUid, ts, to, amount}`; the machine moves the credit and writes both cards (credit, log, `xfer/<key>` marker), see the brewpass README "Credit transfer". Offered only when `remote/<dev>/online.transfer` is true. The log shows transfers separately and the admin statistics do not count them as coffees.
- It signs in with Firebase **Anonymous** auth (enable it in the console), so under the database rules it can read only its own card, `conf` and `remote/...`.
- The screen is the `userview` module's `UserScreen`: credit, log, statistics and "Kávét kérek" (virtual card tap on machine `machine1`).
- Package `com.example.coffeeui`, registered in the TEST project (`userapp/google-services.json`). For production, add this package to `coffeegg-a4840` and replace the file.
