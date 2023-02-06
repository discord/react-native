#include "NativeTrace.h"

#include <dlfcn.h>

namespace facebook {
namespace react {

class ATraceDynamicImports {
  using BeginSection = void (*)(char const *);
  using EndSection = void (*)(void);

 public:
  explicit ATraceDynamicImports() {
    beginSection_ = reinterpret_cast<BeginSection>(
        dlsym(RTLD_DEFAULT, "ATrace_beginSection"));
    endSection_ =
        reinterpret_cast<EndSection>(dlsym(RTLD_DEFAULT, "ATrace_endSection"));
  }

  static ATraceDynamicImports &instance() {
    static ATraceDynamicImports theInstance;

    return theInstance;
  }

  void beginSection(char const *sectionName) {
    if (beginSection_) {
      beginSection_(sectionName);
    }
  }

  void endSection() {
    if (endSection_) {
      endSection_();
    }
  }

 private:
  BeginSection beginSection_;
  EndSection endSection_;
};

void reactAndroidNativeBeginTraceSectionHook(const std::string &sectionName) {
  ATraceDynamicImports::instance().beginSection(sectionName.c_str());
}

void reactAndroidNativeEndTraceSectionHook() {
  ATraceDynamicImports::instance().endSection();
}

} // namespace react
} // namespace facebook