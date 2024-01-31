This projects is a laboratory to test various ideas around http://prize.hutter1.net/

The goal :

- Train ourselves into compression techniques

# The Hutter prize

The goal is to compress [enwik9.zip](http://mattmahoney.net/dc/enwik9.zip) as much as possible, which is a 1GB file.
A previous version of the prize was over [enwik8.zip](http://mattmahoney.net/dc/enwik8.zip), which is a 1GB file.

# Previous work

Previous submitters has open-source all or part of their work. We rely on multiple items of these previous works.

## `phd9`

`phd9` has not been open-sourced (http://prize.hutter1.net/hfaq.htm#getsource).

The preprocessing has been merged into `starlit`: https://github.com/amargaritov/starlit/blob/master/src/readalike_prepr/phda9_preprocess.h

## [cmix](https://github.com/byronknoll/cmix)

`cmix` targets as compressing with best compression ratio, at the cost of CPU, RAM and time.

It relies essentially on:

- LSTM (Long-term memory model) used to guess the next byte given previous content
- 2k+ models, each specialized to specific type of content (exe, text, etc)
- Context-mixing to switch dynamically to the optimal model

## [Starlit](https://github.com/amargaritov/starlit)

`starlit` is based on [cmix](https://github.com/byronknoll/cmix), with additional optimizations like:

- Re-ordering of articles based on Doc2Vec and Travel Salesman Problem to find an optimal way to go through articles, given most compression rely on contexts, and contexts works better if similar content is grouped.
- 

The submitted order of articles can be found at: https://github.com/amargaritov/starlit/blob/master/src/readalike_prepr/data/new_article_order

# Design decisions

- We target being able to run our program easily from a MacOs laptop, through a Java program (given any IDE)
- We do not target doing a formal Hutter-prize sunmission as it requires providing a native Linux executable, which can be painful to produce with efficient size given Java program
- We plan to re-use various inputs from previous submissions like: `cmix` from Homebrew, article re-ordering from Starlit github repository, etc

## New ideas

