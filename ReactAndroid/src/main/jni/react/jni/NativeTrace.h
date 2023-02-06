#pragma once

#include <string>

namespace facebook {
namespace react {

void reactAndroidNativeBeginTraceSectionHook(const std::string &sectionName);
void reactAndroidNativeEndTraceSectionHook();

} // namespace react
} // namespace facebook