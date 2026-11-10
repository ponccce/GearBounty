# GearBounty

Player bounties for Paper servers: put money on someone's head, hunt them down, claim the reward.

- **Bounty board GUI**: `/bounty` opens a paged board with the heads of the most wanted players.
- **Stacking bounties**: many players can add to the same bounty.
- **Anti-abuse rules**:
  - you can't put a bounty on yourself;
  - there is a minimum and a maximum amount;
  - a claim tax is taken from the reward;
  - killing an alt account on the same IP pays nothing;
  - killing your own target only refunds your own money, so it never makes a profit.
- **Expiry with refunds**: an unclaimed bounty goes back to whoever placed it after N days.
- **Vault economy**: works with EssentialsX, CMI and any Vault or VaultUnlocked economy.
- **PlaceholderAPI**: `%gearbounty_amount%`, `%gearbounty_count%`, `%gearbounty_top_1_name%`, `%gearbounty_top_1_amount%`.
- **Translations**: English and Brazilian Portuguese are included. Every message uses MiniMessage and can be edited.
- **SQLite storage**: no database setup needed. Saving happens off the main thread.
- **Folia support**: it only uses Paper's region and async schedulers.

## Compatibility

| | |
|---|---|
| Server | Paper, Purpur, Folia and other Paper forks |
| Minecraft | 1.21.4 to 26.3 (one jar) |
| Java | 21 or newer |
| Requires | Vault (or VaultUnlocked) plus an economy plugin |
| Optional | PlaceholderAPI |

## Commands and permissions

| Command | Permission | Default |
|---|---|---|
| `/bounty` | `gearbounty.use` | everyone |
| `/bounty set <player> <amount>` (accepts `10k`, `2m`) | `gearbounty.use` | everyone |
| `/bounty check [player]` | `gearbounty.use` | everyone |
| `/bounty top` | `gearbounty.use` | everyone |
| `/bounty remove <player>` (refunds everyone) | `gearbounty.admin` | op |
| `/bounty reload` | `gearbounty.admin` | op |

## Building

```bash
./gradlew build        # jar in build/libs/
./gradlew runServer    # local Paper 26.3 test server
```

## How this was made

GearBounty is developed by **GearBlock** with AI assistance. Every release is reviewed and tested by a person on a real server before it is published.

Need a custom plugin, a 26.x migration or a server setup? Contact GearBlock (links coming soon).

## License

MIT
