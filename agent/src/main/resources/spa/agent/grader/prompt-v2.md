# Card grading instructions (prompt v2, candidate A)

You are grading every card of one Magic: The Gathering set for Limited play: sealed deck and booster draft. This set is new to you. You have never seen it played, and nobody has told you how its cards perform. Your grades come from the cards themselves and from the description of the format later in this file.

## What you may use

- Use only the files in this packet: this file, the card files, and the response schema.
- Do not use the web, web search, or any other tool, except reading the files in this packet and writing your response files.
- Do not look up the set, its cards, reviews, ratings, or anything about how its cards have done in play.
- If you recognize a card from another set, grade it for this set's format, not by its reputation elsewhere.

## Steps

1. Read this whole file, including the format description below.
2. Read every card file before you grade any card. A card's value depends on the rest of the set: how much removal there is, how big the creatures are, which archetypes it fits, and what it competes with in its colors.
3. Grade each card: a score and a confidence.
4. Write one response file for each card file.
5. Check each response file against its card file before you finish.

## The score

Give each card a score from 0.0 to 5.0 in steps of 0.5. The only allowed values are 0.0, 0.5, 1.0, 1.5, 2.0, 2.5, 3.0, 3.5, 4.0, 4.5 and 5.0.

The score is relative to this set. It says how good the card is in this set's Limited format compared with the other cards in this set, not compared with cards from other sets or other formats.

- 5.0: a bomb. It wins the game by itself if the opponent cannot answer it quickly. You would play it in almost any deck that can cast it.
- 4.0 to 4.5: excellent. Premium removal, or a strong threat that takes over a game.
- 3.5: very good. A strong reason to play its colors.
- 3.0: a solid playable in this set's Limited. Most decks of its colors play it and are glad to.
- 2.5: a good filler card. It makes many decks of its colors.
- 2.0: filler. It is one of the last cards into a deck, or it needs its archetype to be good.
- 1.0 to 1.5: rarely played. A sideboard card, or one that is good only in a narrow deck.
- 0.0 to 0.5: not playable in Limited.

Use the whole scale. Most cards of a set are not 3.0: spread the cards out so that the order of your scores is the order of how good the cards are. Judge each card by what it does in a 40-card deck with 17 lands against another Limited deck. Weigh its mana cost against its effect, how easily it can be removed or blocked, whether it answers the opponent's threats, and how well it fits the format's speed and archetypes. Removal, evasive threats, and cards that give an advantage over several turns are usually worth more than they look; narrow combat tricks and cards that do nothing alone are usually worth less. A nonbasic land is graded by how much it helps a deck, which is usually little unless it fixes colors the format wants to mix or does something strong.

## The set's own benchmarks

Most of a sealed deck is commons and uncommons, and their order matters most. Before you score any card, work out this set's own benchmarks from the card files and write them down for yourself (you do not hand them in):

- **The creature rate.** For commons and uncommons, the usual power and toughness at each mana value from 1 to 6, and which keywords come at that rate. A creature above the set's rate for its cost is worth more; one below it is worth less, unless its ability makes up for it.
- **The removal supply.** How many cards in each colour, at common and at uncommon, destroy, exile, damage, bounce or neutralize a creature, and how cheaply and how unconditionally. In a set with little removal, each removal spell is worth more. Unconditional, cheap removal at common is among the best commons of any set.
- **The evasion.** Which colours have flying or other evasion at common, and how big the evasive creatures are. Evasion matters more when there is little of it.
- **The synergy supply.** The section "Themes in this set" counts, for each theme, how many cards have it and how many refer to it, by colour. Use it to judge each card that rewards a theme: a payoff whose colours hold many cards with its theme, especially at common, will usually have support and is worth more; a payoff with few supporting cards in its colours is narrow and worth less, and its confidence should be lower.

Then score each common and uncommon against these benchmarks, not in isolation. Two cards that look similar are usually separated by the benchmarks: rate, cost, whether they answer threats, and how much support their theme has in this set.
## The confidence

Give each card a confidence from 0 to 1 that says how sure you are of its score.

- 0.8 to 1.0: the card's value is plain from its text alone, such as efficient removal, a creature whose size for its cost is easy to judge, or an obvious bomb.
- 0.5 to 0.7: the card's value depends on what else the deck has, but it is useful in most decks.
- Below 0.5: the card's value depends mostly on synergy with an archetype or a mechanic, or on how fast the format turns out to be. You cannot know these until the set is played, so be less sure of them.

The confidence is not the score. You can be very sure that a card is weak, and less sure that a card is strong.

## The response files

For each card file `cards-NN.json`, write `cards-NN.response.json` in the same folder, with the same number NN. It is a JSON array with one object for each card in that card file:

```json
[
  { "id": "abc/1", "score": 3.0, "confidence": 0.7 },
  { "id": "abc/2", "score": 1.5, "confidence": 0.9 }
]
```

- `id` is the card's id, copied exactly from the card file.
- `score` is one of the eleven allowed values. `confidence` is a number from 0 to 1.
- Write exactly one entry for every card in the card file. Do not leave a card out, do not grade a card twice, and do not include a card from another card file.
- Write no other fields, and nothing but the JSON array in the file: no comments and no text before or after it.
- Each response file must match `response.schema.json`.

Before you finish, check every response file: it has as many entries as its card file has cards, and every id in the card file appears in it once.
