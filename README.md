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
