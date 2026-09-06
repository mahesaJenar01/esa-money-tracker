# Esa's Money Tracker

A personal income and expense tracker for two pockets — **Online**, split across
as many banks as you actually have, and **Tunai** (cash) — with everything in
rupiah.

## What it does

- **Saldo awal** — on a genuinely empty app (a fresh install, or data that was
  wiped) the first screen asks what is already there: the banks holding the
  online money, one at a time, and how much cash is in the wallet. The answer is
  stored apart from the notes: it lifts the balances without ever appearing in
  Riwayat or in the weekly analytics. It is asked once, keyed to the *data* and
  not to the install, so updating or reinstalling over existing notes never
  brings it back.
- **Bank** — the Online tile on the home screen opens a page listing every bank
  with what it holds and how many notes were recorded there. See
  [Banks](#banks).
- **Home** — a gradient header with the total balance and a tile per pocket, an
  analytics card for the selected period, the most recent entry called out on its
  own card, and **this week's** history grouped by day. Tapping an entry opens
  **Ubah** and **Hapus** right underneath it, along with the date it was created.
- **Riwayat** — a row leads with the short, predictable facts — category, bank or
  pocket, time — and puts the description on the line below, clipped to one line.
  Tapping it opens the description in full, says when the note was written,
  backdated or last changed, and offers **Ubah** and **Hapus**.
- **Riwayat lengkap** — the *Semua* button opens the full history, one week per
  screen with a total for that week, walked back a week at a time. Older weeks
  stop where the oldest note is, so paging always has an end.
- **Catatan terhapus** — deleting moves a note to a 30-day bin instead of
  destroying it. The bin says how long each note has left and puts it back in the
  exact position it was deleted from; a snackbar offers the same undo right after
  a delete.
- **Cek saldo** — the weekly reconciliation. It asks what each bank *really*
  holds, next to what the app worked out, offers to write the gap into the
  history as an ordinary note, and leaves a line across Riwayat where it was
  done. See [Marking where the balances were checked](#marking-where-the-balances-were-checked).
- **Analytics** — a period picker that currently offers **Mingguan** (weekly).
  The card shows income, expense, the difference, and a per-category breakdown of
  spending in that window.
- **Pindah dana** — money moved between your own pockets: bank to bank, *setor
  tunai*, *tarik tunai*. It is not income and not an expense, it has its own
  history, and it never appears in Riwayat. See [Pindah dana](#pindah-dana).
- **Catat** — the floating button opens a three-step flow, or leaves it at the
  first step for a transfer:
  1. Pemasukan, Pengeluaran, or Pindah Dana?
  2. Category — *Gaji* / *Pendapatan Lainnya* for income; *Jajan*, *Operational*,
     *Makan*, *Langganan*, *Kebutuhan*, *Belanja*, *Lainnya* for expenses.
  3. Amount (digits only, formatted to rupiah as you type), which pocket — and
     for Online, which **bank** — the money moved through, **when** it happened,
     and a **mandatory** description. Submitting returns to the home screen.
- **Ubah** — editing opens a single page holding everything that can change:
  type, category, amount, pocket, bank, time and description. Saving rewrites the
  note **in place**: it keeps the date it already had and simply gains a mark
  saying it was changed. The button stays disabled until something is actually
  different, so opening a note and backing out through *Simpan* cannot brand it
  as edited. See [The three timestamps](#the-three-timestamps).

## Layout

```
app/src/main/java/com/esa/moneytracker/
├── data/
│   ├── model/        Transaction, TransactionType, Pocket, Category, Bank,
│   │                 OnlinePocket, BalanceCheck
│   ├── local/        Room entities, DAOs, database and its migrations
│   ├── repository/   TransactionRepository — the only way in or out of storage
│   └── export/       BackupDocument, TransactionExportRecord, ExportFormat
├── ui/
│   ├── theme/        colours, type scale, shapes, semantic MoneyColors
│   ├── components/   reusable pieces (balance header, rows, badges, form fields)
│   ├── setup/        the opening-balance question and the gate that shows it
│   ├── banks/        the bank page and every dialog that changes one
│   ├── transfer/     pindah dana — the form and its own history
│   ├── check/        cek saldo — the reconciliation that leaves a mark
│   ├── backup/       data & cadangan — export, import, document reading
│   ├── home/         home screen, its sections, HomeViewModel
│   ├── entry/        the three-step input flow, EntryViewModel
│   ├── edit/         the single-page edit screen
│   ├── records/      the week-by-week full history
│   ├── bin/          catatan terhapus, the 30-day bin
│   └── navigation/   NavHost wiring
└── util/             rupiah formatting, Indonesian dates, AnalyticsPeriod, WeekWindow
```

## Banks

The Online pocket is not a figure of its own. It is the sum of the open banks,
and a bank's balance is the sum of three things:

```
saldo bank = saldo awal + koreksi
           + setiap catatan yang tercatat di bank itu
           + setiap pindah dana masuk, dikurangi yang keluar
```

Nothing stores a balance directly, which is what makes the breakdown on the bank
page and the number on the home screen incapable of disagreeing. Every online
note names a bank; the entry flow picks it for you when you only have one. The
wallet is worked out the same way, from the opening cash figure, the cash notes
and the transfers in and out of it — see [Pindah dana](#pindah-dana).

**Adding a bank** asks one question that matters: is this balance *tambahan* or
*sudah termasuk* the online total already? New money lifts the total. Money that
was already being counted somewhere else has to come off that bank, so the app
asks which one — without that answer, "the total stays the same" could not be
true and the difference would simply go missing.

**Removing a bank** asks the mirror image. *Kurangi* means the money is gone and
the online total falls by exactly what the bank held. *Pindahkan* means it moved,
and the destination is required: the balance **and** the notes recorded there
both go to the chosen bank, leaving the total untouched.

Either way the bank row survives, marked closed. Old notes keep naming the bank
they happened in, and Riwayat is never rewritten — a closed bank simply stops
counting towards the balance.

**Correcting a balance** is stored as an adjustment on top of the opening figure
rather than by rewriting it, so what the bank was first said to hold stays
readable.

### Upgrading from before banks existed

An install that already holds data is folded into a single bank named **Online**
by the v3 migration: it takes the whole online opening balance and adopts every
online note ever written. The total does not move by a rupiah and no screen
interrupts the user. The bank page shows a nudge to rename or split it, which
disappears by itself as soon as the bank is renamed or a second one is added.
The same fold runs after importing a backup written by that older version.

## Pindah dana

Moving money between your own pockets is not something that happened to your
finances. Nothing was earned and nothing was spent — the total across every
pocket is the same rupiah before and after — so it is kept out of everything
that measures income and expense:

- its own table, `transfers`, never `transactions.type`;
- its own history page, reached from **Uang online**, never Riwayat;
- never in *Catatan terakhir*, never in the analytics, never in the category
  breakdown;
- written plainly in the list, with no `+`, no `-`, and no green or red.

Keeping it in a separate table is what makes all of that true by construction.
Sharing one with the notes would mean every total in the app had to remember to
exclude transfers, and one day one of them would forget.

Three shapes, told apart only by which end is cash:

| From | To | Called |
| --- | --- | --- |
| bank | bank | Pindah antar bank |
| Tunai | bank | Setor tunai |
| bank | Tunai | Tarik tunai |

A null bank id **is** Tunai, which is why both ends cannot be null: cash to cash
is not a move, it is nothing happening.

What a transfer does change is the breakdown. A bank's balance counts the moves
in and out of it exactly as it counts the notes recorded against it, and so does
the wallet — which is what keeps the banks adding up to the Online tile after
money has been shifted around.

The amount is checked against what the app thinks the source holds, but only as
a warning. The app's idea of a balance is no better than what has been recorded
in it, so refusing a move it believes you cannot afford would be the app arguing
with your bank. It says the number and lets you decide.

Editing and deleting work like a note's: the date only moves when you move it,
an edit leaves a *pernah diubah* mark rather than jumping the row to today, and
deleting puts it in the same 30-day bin — where restoring it puts the money back
exactly where it was.

## The three timestamps

Every note carries three, and they answer different questions:

| Column | Meaning | Changes? |
| --- | --- | --- |
| `occurred_at` | when the money actually moved | only when the user says so |
| `created_at` | when the note was written | never |
| `updated_at` | when it was last edited | on every edit |

`occurred_at` is the only one the list sorts and groups by, and the entry flow
lets it be set to any date and time. A purchase from the 1st entered on the 5th
therefore lands on the 1st, where it belongs, rather than pretending to be
today's.

Editing does **not** touch `occurred_at`. Fixing a wrong amount on a note from
the 1st leaves it on the 1st; it just gains `updated_at`. The two marks are
independent and both can be true at once — a note remembered late *and* later
corrected says so on both counts when it is opened.

A note written normally gets one single `Instant` for both `occurred_at` and
`created_at`, which is why "was this backdated?" is an exact comparison rather
than a guess about how long the form took to fill in. A minute of slack is
allowed for rows written before v3, whose two stamps could land a millisecond
apart by accident; the v3 migration also reads a *large* gap in an old row as
what it always was — an edit — and writes it into `updated_at`.

## How deleting works

Deleting sets `deleted_at` and nothing else. The row keeps its id, its dates and
its place in the ordering, so restoring is a matter of clearing that one column
and the record reappears exactly where it was — there is no re-insert and no new
id. Rows are removed for good only once they have been in the bin for 30 days,
which is swept on launch and whenever the bin is opened.

Notes and transfers share the bin, in one list ordered by when each was thrown
away. Both belong there for the same reason: deleting a transfer moves money
back between pockets just as surely as deleting a note changes a total, so both
have to be as recoverable as each other.

## Marking where the balances were checked

A transaction that is never written down does not announce itself. It shows up
weeks later as a bank saying one number and the app saying another, with no way
to tell *when* the two stopped agreeing — so the search is the whole history
rather than the week that actually contains the mistake.

**Cek saldo** is the answer to that. It asks, once per open bank and once for
Tunai, what the pocket really holds. Every row is optional: a bank left blank was
not counted and gets no line in the record, which keeps the mark an honest
account of what was actually verified rather than a claim about everything.

Where the two figures disagree, the screen offers to close the gap by writing an
ordinary note — **Pendapatan Lainnya** when the bank holds more than the app knew
about, **Lainnya** when it holds less, described as `Selisih saldo <bank>`. That
is deliberately a note and not a bank correction: a correction hides inside the
bank's arithmetic, while a forgotten transaction is history, and history belongs
in Riwayat where it can be found, edited, or deleted once it is remembered. The
switch can be turned off per bank, for a gap worth hunting down before it is
papered over.

Saving draws a line across Riwayat at the moment of the check:

```
        ──────  Saldo dicek • 20:14  ──────
              4 kantong dicek, semuanya cocok
```

The line is the point of the whole feature. **Everything above it has not been
checked against a bank yet**; everything below it was true when it was drawn. A
total that stops matching next week is therefore a transaction somewhere in the
stretch above the newest line, not somewhere in a year of records.

Tapping the line opens what it recorded: per bank, what the app thought it held
and what the bank said, the gap, and whether that gap was written down. The bank
names are copied into the record rather than looked up, so renaming or closing a
bank later never makes an old check illegible.

### Where the notes land

A note written to close a gap is dated to the check itself, which puts it
**below** the line. That is the grammar of the thing: it explains something that
happened in the stretch that was just verified, not in the unchecked stretch that
starts above. In a tie on the clock the mark sorts above the note, which is what
makes that true.

Deleting a mark deletes only the mark. Any note it wrote stays exactly where it
is, because that note is a claim about money that moved — removing it would
quietly change every balance since. It can be deleted on its own from Riwayat if
it really was wrong.

### Where it is reached from

The bank page carries the button, under the total, because that is where the
figures being compared are listed; the card there says when the last check was
and how many notes have been written since — the exact stretch a new gap would be
hiding in. Riwayat lengkap carries the same button, next to the bin, and says the
same thing above the week it is showing.

Nothing is backfilled on upgrade. A mark says somebody sat down and compared the
app against a bank, and there is no honest way to invent one for a week nobody
checked — so an install has no marks until the first check is made.

## Adding monthly and yearly analytics

`util/AnalyticsPeriod.kt` is an enum where each constant owns its own date range:

```kotlin
MONTHLY("monthly", "Bulanan") {
    override fun startOf(today: LocalDate) = today.withDayOfMonth(1)
    override fun endExclusiveOf(today: LocalDate) = startOf(today).plusMonths(1)
    override fun subtitle(today: LocalDate) = "Bulan ini"
},
```

Adding that constant is the whole change — the picker lists
`AnalyticsPeriod.entries`, and the view model filters by
`period.contains(date, today)`.

## Exporting and importing

**Data & cadangan** — the round button in the top right of the balance header —
writes the whole app out to a file, or reads one back in. Both directions go
through the system file picker, so the app never chooses a location itself, no
storage permission is asked for, and the file can land in Downloads, Drive, or
anywhere else.

| Format | Contains | Importable |
| --- | --- | --- |
| `.json` | opening balances, banks, balance checks, transfers **and** every live note | yes |
| `.csv` | one row per note, nothing else | no — for spreadsheets |

Importing **merges by id**: a note already on file is replaced by the version in
the backup and anything new is added, so importing the same file twice leaves the
same result rather than a doubled history. Banks and balance checks merge the
same way — a restored history that had forgotten where it was last reconciled
would send you back through every week of it. The opening
balance in the file replaces the current one — restoring a backup that did not
restore the starting balances would leave every total wrong. Notes in the 30-day
bin are not exported; closed banks are, because live notes still point at them.

Format version 4 added the transfers; a file written before it simply has none,
which is not the same as "they were deleted" — an import only ever adds and
replaces.

**Old files still work.** A file written before format version 3 carries no
balance checks, which means the backup predates the mark rather than that the
checks were lost — nothing is ever removed on import, only merged in. A
format-version 1 file predates banks: it carries the
whole online balance as one figure and names no bank on any note. It is accepted
and converted rather than refused — the figure and the notes are folded into a
single bank, exactly as an in-place upgrade does, and the import message says
which bank they landed in. From format version 2 on, `opening_balance.online` is
written as zero because the online money lives in the banks.

Nothing is trusted on the way in. A file that is not JSON, was written by another
app, or claims a newer format version is refused with a message rather than a
crash, and an individual row missing an id, a readable timestamp, or with a
negative amount is skipped instead of written.

On an empty app the first screen offers both ways to start: type the opening
balances, or pick a backup file. A successful import satisfies exactly what that
screen is gating on, so the app moves straight on with the restored data.

### The shapes involved

- `BackupDocument` is the file: `app`, `format_version`, `exported_at`, the
  opening balance, the banks, the transactions, the balance checks and the
  transfers. Unknown JSON keys are ignored on read, so a file written by a later
  version still loads what it can, and a missing `banks` array is what marks a
  file as pre-bank.
- `BalanceCheckExportRecord` is one reconciliation, with its per-bank lines
  nested inside it rather than in a second array — a check without its lines says
  almost nothing, and the two halves are written and read as one. Its lines are
  replaced wholesale on import rather than merged one by one, so a check cannot
  end up carrying a line from an older version of itself.
- `BankExportRecord` is one bank. It carries the opening amount and the
  corrections rather than the balance, because the balance is derived from notes
  in the same file and storing it too would let the two disagree after a merge.
- `TransferExportRecord` is one move between pockets. Restoring these matters as
  much as restoring the notes: a transfer changes nothing about how much money
  exists and everything about which bank holds it, so a backup without them
  would put the right total in the wrong places. A row whose two ends are the
  same is skipped — it would move nothing.
- `TransactionExportRecord` is one note, in both directions — it carries machine
  ids *and* human labels so the file is readable without the app, plus ISO
  timestamps precise enough to parse back exactly. `toEntity()` is the import
  half and returns null for a row that cannot be trusted.
- `ExportFormat.JSON` / `ExportFormat.CSV` render a document to a body. They are
  pure functions with no Android dependencies.
- `TransactionRepository.exportBackup()` / `importBackup()` are the only ways in
  and out of storage.

Every row's primary key is a client-generated UUID, which is what makes merging
by id safe across phones and across reinstalls.

## Updating the app on your phone

Installing an APK by tapping it is **not** developer mode and **not** USB
debugging. It is one ordinary permission — *Install unknown apps* — granted to
whichever app opens the file (Chrome, Files, Drive), and it can be revoked
straight afterwards. A phone that has never had developer options enabled
installs and updates this app perfectly well.

What makes it an *update* rather than a fresh install, and therefore what keeps
every recorded transaction, is two things the build now handles on its own: the
APK is signed with the same key every time, and its `versionCode` goes up on
every release. See [Releasing](BUILDING.md#releasing).

So the loop is:

1. `run.bat release` on the PC — leaves `dist\esa-money-tracker-<version>.apk`.
2. Get that file to the phone.
3. Open it on the phone and tap install. It lands on top of the existing app,
   data intact. No uninstall, no Smart Switch, no cable.

### Getting the file there

Releases live at
[github.com/mahesaJenar01/esa-money-tracker/releases](https://github.com/mahesaJenar01/esa-money-tracker/releases),
and the phone watches that page through
[Obtainium](https://github.com/ImranR98/Obtainium) — a free app that does for a
personal APK what the Play Store does for everything else: it notices a new
release, says so, and installs it in one tap.

Setting it up on the phone, once:

1. Install Obtainium from
   [its own releases page](https://github.com/ImranR98/Obtainium/releases)
   (the `...-release.apk` file).
2. **Add App** → paste `https://github.com/mahesaJenar01/esa-money-tracker`
   → **Add**.
3. Grant Obtainium *Install unknown apps* when it asks. That is the only
   permission involved, and it is not developer mode.

From then on: publish a release on the PC, and the phone offers the update.

Dropping the file into Drive and opening the link, or Quick Sharing it from the
PC, both work too and need no setup — they just do not tell you when there is
something new.

The repository is public, so the APK is a public download. There is nothing
personal in either — the app keeps every rupiah on the phone and has no server
to send it to. Your recorded transactions live only in the app's own database
and in whatever `.json` backups you export yourself.

### Before you change the signing key

Changing the key is the one thing that forces an uninstall, and an uninstall
takes the data with it. If it has to happen, the app already has the way
through: **Data & cadangan → export a `.json`** first, then uninstall, install
the new APK, and import the file back. The import restores the banks, the
opening balances and every note.

## Building and installing

```
run.bat
```

That builds the debug APK, installs it over any existing copy (your recorded
data survives), and launches it. `run.bat build` builds without touching the
phone; `run.bat clean`, `run.bat install` and `run.bat release` do what they say.

See [BUILDING.md](BUILDING.md) for the toolchain and for a JDK problem on this
particular PC that stops *any* Gradle build — including Android Studio's — until
one environment variable is set. `run.bat` works around it on its own.
