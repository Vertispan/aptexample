A contrived example of an annotation processor, demonstrating quickly how one might structure a project and wire
up the processor to help generate code for a project. Also demonstrates a bug in IntelliJ where it fails to
respect Filer.createSourceFile's originatingElements parameter, though Eclipse does it correctly (as do command
line tools that I'm aware of).

Steps to reproduce:
 * check out the project (notice that SampleProcessor includes originating elements in logs, so you can confirm what should be updated)
 * Edit "App" interface (comment or uncomment Thing2() method)
 * Build > Make Project
 * Confirm App_Impl was updated accordingly each time
 * Edit Thing class (add or remove a method)
 * Build > Make Project

Expected result:
App_Impl.java is correctly updated

Actual result:
App_Impl.java does not rebuild.
