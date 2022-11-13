// Code generated by Deager. Do not edit.
package se.ansman.binding;

import dagger.Lazy;
import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.codegen.OriginatingElement;
import dagger.hilt.components.SingletonComponent;
import dagger.multibindings.IntoSet;
import se.ansman.deager.Initializable;

@Module
@InstallIn(SingletonComponent.class)
@OriginatingElement(
    topLevelClass = BindingModule.class
)
public abstract class EagerBindingModule {
  private EagerBindingModule() {
  }

  @Provides
  @IntoSet
  public static Initializable bindThingAsInitializable(Lazy<Thing> thing) {
    return Initializable.fromLazy(thing);
  }
}