/**
 * Global shared kernel: the CQS abstractions ({@code Command}, {@code CommandHandler}) reused by
 * every module. Declared as an OPEN Spring Modulith module so any module may depend on it without
 * violating module boundaries.
 */
@org.springframework.modulith.ApplicationModule(type = org.springframework.modulith.ApplicationModule.Type.OPEN)
package io.github.ryu200o.eduworkshop.shared;
