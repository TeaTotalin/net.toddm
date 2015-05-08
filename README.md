
For more detailed documentation see the Wiki.


<h4>---- Goals ----</h4>
I set out to create a portable, versatile, resilient Communications Framework.  I started out from the mobile
client perspective where things like failure retry and tolerance to poor network quality have a great deal
of importance.  I decided, however, that I wanted the framework to be easily implementable for server
side use scenarios as well, such as service to service communications, etc.

Some of the features I wanted the framework to include, in no particular order, are:
  - Strong Request identity
      - Allows for the prevention of duplicate requests
      - Allows for returning result wait handles to any number of interested parties
      - Very useful as cache keys for result caching, etc.
  - Response caching
      - Implemented in a pluggable, extensible way such that all possible persistence stores can be supported
      - Supports common caching mechanisms such as TTL, LRU, etc.
      - Support for common caching related HTTP headers and responses such as "Cache-Control", "ETag", 304 
        "Not Modified", etc.
  - Priority queue of pending requests
      - Implemented in a pluggable, extensible way to support different use cases
      - Default implementation available that guards against starvation and does basic time based promotion
  - Graceful recovery from bad network, service outage, etc.
      - Implemented in a pluggable, extensible way to support different use cases
      - Short-term retry for transient network issues
      - Longer-term response based retry (503, 202) with support for "Retry-after" header
  - Redirect support for 301, 302, and 303 responses
  - GZIP support for both requests and responses
  - Analytics support for anticipating on-the-wire cost, request frequencies, etc.
  - Support canceling requests


<h4>---- A note about SLF4J ----</h4>
One of my goals in creating these frameworks is to make them as compatible and portable as possible.
That is why you may notice things like including a base 64 implementation rather than using one of the
many libs that provide this.  The one external lib I chose to include is SLF4J.  I made the decision 
to use SLF4J because it is very widely supported, including a port for Android, the first platform specific
implementation I planned.

If your target is a standard Java deployment then you are welcome to simply use the SLF4J JARs included in 
the projects.  If you are targeting a platform that requires a platform specific port, such as Android,
then you can export the project JARs without the included SLF4J JARs and separately include the SLF4J port
you need.

I've included an older SLF4J version, but it should be safe to use this code base with newer versions as
desired.


<h4>---- CachingFramework ----</h4>
This is a fairly thin framework defining basic caching.  This framework only provides cache providers that
can be implemented in a standard Java, platform independent way, such as an in-memory backed provider.
Platform specific implementations of the caching interface defined here are found in the platform specific
projects.  For example, the Android framework project provides a SQLite backed implementation.


<h4>---- CommFramework ----</h4>
This is where the meat is.  This is the Communications Framework itself providing most of the functionality
described in "Goals" above.  This project, coupled with the Caching Framework, provides fully functional,
feature rich communications capabilities.  Platform specific extension projects, such as the one provided for 
Android, are only required if platform specific implementation is required (such as SQLite caching persistence).
This project is standard Java without platform dependencies.  For most use cases, if caching is not needed, or 
standard Java caching persistence is sufficient then this is a fully functioning communications solution.


<h4>---- AndroidCommFramework ----</h4>
This is a platform specific extension of the Communications Framework for Android.  This project also
includes a sample Android app that makes use of the comm framework.  The primary thing provided by this 
framework extension is a SQLite backed caching provider implementation.


<h4>---- Sample Clients ----</h4>
TBD

A very simple sample for Android can be found in the Android project.

