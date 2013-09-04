# Typesafe Activator

This project aims to be the snappiest snaptastic snapster you've every snapping laid eyes on!  And by that, we mean it builds stuff.

For more information on using Activator, visit: [http://typesafe.com/activator](http://typesafe.com/activator)

# Activator Developer Documentation

This project uses [SBT 0.12](http://scala-sbt.org).   Make sure you have an SBT launcher, and run it in the checked out directory.


## Running the UI

    sbt> project activator-ui
    sbt> run

or just

    sbt "activator-ui/run"


## Testing

There are two types of tests:  Unit tests and integration tests.

### Unit Tests

To run unit tests, simply:

    sbt> publish-local
    sbt> test

To run the tests of a particular project, simply:

    sbt> <project>/test

To run a specific test, simply:

    sbt> test-only TestName

## Integration Tests

To run all the integration tests, simply:

    sbt> integration-tests



## Staging a distribution

    sbt> activator-dist/stage

or just

    sbt> stage 

*Note: just stage will also run `activator-ui/stage`*

Generates a distribution in the `dist/target/stage` directory.

## Building the Distribution

First, make sure to start SBT with a *release version* specified.  By default, activator will created a dated version.  Here's
an example command line:

    sbt -Dactivator.version=1.0.1

Now, simply run one of the command to create a distribution.

    sbt> activator-dist/dist

or just

    sbt> dist

*Note: just stage will also run `builder-ui/dist`*

Generates the file `dist/target/universal/typeasafe-activator-<VERSION>.zip`.

## Publishing the Distribution

First, make sure your credentials are in an appropriate spot.  For me, that's in `~/.sbt/user.sbt` with the following content:

    credentials += Credentials("Amazon S3", "downloads.typesafe.com.s3.amazonaws.com", <AWS KEY>, <AWS PW>)

Then you can run simply:

    sbt> activator-dist/s3-upload

*OR*

    sbt> s3-upload
    

## Publishing NEWS to versions

First, edit the file `news/news.html` to display the news you'd like within builder.

Then run:

    sbt> news/publish-news <version>


# Issues

If you run into staleness issues with a staged release of Activator, just run `reload` in SBT to regenerate the version number and then run `stage` again.   This should give you a new stable version of SNAP for the sbt-launcher so that the new code is used.   Should only be needed when doing integration tests.
