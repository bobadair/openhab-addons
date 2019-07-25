# ThreadTestNew Binding

This binding was created to help identify scheduler thread pool issues.

## Supported Things

**threadtest** is the only supported thing.

## Discovery

_n/a_

## Binding Configuration

_None required_

## Thing Configuration

The **threadtest** things takes the required parameter _interval_ which is of type _integer_. This specifies the periodic job interval in seconds.

## Channels

Only one channel is created for a **threadtest** thing.

| channel   | type   | description                                                                               |
|-----------|--------|-------------------------------------------------------------------------------------------|
| poolStats | String | A string sent to this channel triggers logging on job and thread pool status information  |

