# Gabbler #

Gabbler is a simple push-enabled chat application showcasing a modern and reactive web application: an interactive client based on [AngularJS](http://angularjs.org) and a scalable and resilient RESTful server written in [Scala](http://www.scala-lang.org), [Akka](http://akka.io) and [spray](http://spray.io).

For details check out the [Gabbler, a reactive chat app – part 1](http://hseeberger.github.io/blog/2013/07/08/gabbler-part1/) blog post and its follow-ups.

Huge thanks to Mathias Doenitz from the spray team for his help and contributions!

## Running Gabbler ##

Clone this repository, `cd` into it and start [sbt](http://www.scala-sbt.org). Then simply execute `run` to start the Gabbler server:

```
gabbler$ sbt
[info] ...
gabbler> run
[info] Running de.heikoseeberger.gabbler.GabblerServerApp
```

Open a browser and point it to [localhost:8080/](http://localhost:8080/). Use the same value for username and password, e.g. "John" and "John".

Enter a message in the text area on the left, click the "Gabble away" button and watch the message appear on the right.

Open a second browser window which needs to be a different application if you want to use a separate login (e.g. first Safari, second Chrome), enter another message and watch it appear in both browser windows.

To stop the Gabbler server, send a GET request to [localhost:8080/api/shutdown](http://localhost:8080/api/shutdown).

## Contribution policy ##

Contributions via GitHub pull requests are gladly accepted from their original author. Along with any pull requests, please state that the contribution is your original work and that you license the work to the project under the project's open source license. Whether or not you state this explicitly, by submitting any copyrighted material via pull request, email, or other means you agree to license the material under the project's open source license and warrant that you have the legal authority to do so.

## License ##

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").
