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
- **Analytics** — a period picker that currently offers **Mingguan** (weekly).
  The card shows income, expense, the difference, and a per-category breakdown of
  spending in that window.
- **Catat** — the floating button opens a three-step flow:
  1. Pemasukan or Pengeluaran?
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
│   ├── model/        Transaction, TransactionType, Pocket, Category, Bank, OnlinePocket
│   ├── local/        Room entities, DAOs, database and its migrations
│   ├── repository/   TransactionRepository — the only way in or out of storage
│   └── export/       BackupDocument, TransactionExportRecord, ExportFormat
├── ui/
│   ├── theme/        colours, type scale, shapes, semantic MoneyColors
│   ├── components/   reusable pieces (balance header, rows, badges, form fields)
│   ├── setup/        the opening-balance question and the gate that shows it
│   ├── banks/        the bank page and every dialog that changes one
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
saldo bank = saldo awal + koreksi + setiap catatan yang tercatat di bank itu
```

Nothing stores a balance directly, which is what makes the breakdown on the bank
page and the number on the home screen incapable of disagreeing. Every online
note names a bank; the entry flow picks it for you when you only have one.

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

Deleting sets `transactions.deleted_at` and nothing else. The row keeps its id,
its dates and its place in the ordering, so restoring is a matter of clearing
that one column and the note reappears exactly where it was — there is no
re-insert and no new id. Rows are removed for good only once they have been in
the bin for 30 days, which is swept on launch and whenever the bin is opened.

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
| `.json` | opening balances, banks **and** every live note | yes |
| `.csv` | one row per note, nothing else | no — for spreadsheets |

Importing **merges by id**: a note already on file is replaced by the version in
the backup and anything new is added, so importing the same file twice leaves the
same result rather than a doubled history. Banks merge the same way. The opening
balance in the file replaces the current one — restoring a backup that did not
restore the starting balances would leave every total wrong. Notes in the 30-day
bin are not exported; closed banks are, because live notes still point at them.

**Old files still work.** A format-version 1 file predates banks: it carries the
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
  opening balance, the banks, and the transactions. Unknown JSON keys are ignored
  on read, so a file written by a later version still loads what it can, and a
  missing `banks` array is what marks a file as pre-bank.
- `BankExportRecord` is one bank. It carries the opening amount and the
  corrections rather than the balance, because the balance is derived from notes
  in the same file and storing it too would let the two disagree after a merge.
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

| How | Setup | Feels like |
| --- | --- | --- |
| Upload to Drive / any cloud, open the link on the phone | none | a download |
| Quick Share from the PC | none | a transfer |
| Publish as a GitHub release and point [Obtainium](https://github.com/ImranR98/Obtainium) at the repo | a repo, once | the Play Store — it notices new releases and offers the update itself |

The last one is the closest thing to Play Store behaviour without paying the
developer fee: Obtainium watches the releases page, tells you when a new APK is
out, and installs it in one tap.

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
