// Copyright 2012 The ChromiumOS Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include <map>
#include <set>

#include <gtest/gtest.h>  // for FRIEND_TEST

#include "include/filter_interpreter.h"
#include "include/finger_metrics.h"
#include "include/gestures.h"
#include "include/macros.h"
#include "include/tracer.h"

#ifndef GESTURES_PALM_CLASSIFYING_FILTER_INTERPRETER_H_
#define GESTURES_PALM_CLASSIFYING_FILTER_INTERPRETER_H_

namespace gestures {

// This interpreter looks at the location and pressure of contacts and may
// optionally classify each as a palm. Sometimes a contact is classified as
// a palm in some frames, and then in subsequent frames it is not.
// This interpreter is also used to suppress motion from thumbs in the
// bottom area of the pad.

class PalmClassifyingFilterInterpreter : public FilterInterpreter {
  FRIEND_TEST(PalmClassifyingFilterInterpreterTest, ExternallyMarkedPalmTest);
  FRIEND_TEST(PalmClassifyingFilterInterpreterTest, PalmAtEdgeTest);
  FRIEND_TEST(PalmClassifyingFilterInterpreterTest, PalmReevaluateTest);
  FRIEND_TEST(PalmClassifyingFilterInterpreterTest, PalmTest);
  FRIEND_TEST(PalmClassifyingFilterInterpreterTest, StationaryPalmTest);
 public:
  // Takes ownership of |next|:
  PalmClassifyingFilterInterpreter(PropRegistry* prop_reg, Interpreter* next,
                                   Tracer* tracer);
  virtual ~PalmClassifyingFilterInterpreter() {}

 protected:
  virtual void SyncInterpretImpl(HardwareState& hwstate, stime_t* timeout);

 private:
  void FillOriginInfo(const HardwareState& hwstate);
  void FillPrevInfo(const HardwareState& hwstate);
  void FillMaxPressureWidthInfo(const HardwareState& hwstate);

  // Part of palm detection. Returns true if the finger indicated by
  // |finger_idx| is near another finger, which must not be a palm, in the
  // hwstate.
  bool FingerNearOtherFinger(const HardwareState& hwstate,
                             size_t finger_idx);

  // Returns true iff fs represents a contact that may be a palm. It's a palm
  // if it's in the side edge (or top edge if filter_top_edge_ is set) with
  // sufficiently large pressure. The pressure required depends on exactly how
  // close to the edge the contact is.
  bool FingerInPalmEnvelope(const FingerState& fs);

  // Returns true iff fs represents a contact that is in the bottom area.
  bool FingerInBottomArea(const FingerState& fs);

  // Updates *palm_, pointing_ below.
  void UpdatePalmState(const HardwareState& hwstate);

  // Updates the hwstate based on the local state.
  void UpdatePalmFlags(HardwareState& hwstate);

  // Updates the distance travelled for each finger along +- x/y direction.
  void UpdateDistanceInfo(const HardwareState& hwstate);

  // Returns the length of time the contact has been on the pad. Returns -1
  // on error.
  stime_t FingerAge(short finger_id, stime_t now) const;

  // Time when a contact arrived. Persists even when fingers change.
  std::map<short, stime_t> origin_timestamps_;
  // FingerStates from when a contact first arrived. Persists even when fingers
  // change.
  std::map<short, FingerState> origin_fingerstates_;

  // FingerStates from the previous HardwareState.
  std::map<short, FingerState> prev_fingerstates_;

  // Max reported pressure for present fingers.
  std::map<short, float> max_pressure_;

  // Max reported width for present fingers.
  std::map<short, float> max_width_;

  // Accumulated distance travelled by each finger.
  // _positive[0]  -->  positive direction along x axis
  // _positive[1]  -->  positive direction along y axis
  // _negative[0]  -->  negative direction along x axis
  // _negative[1]  -->  negative direction along y axis
  std::map<short, float> distance_positive_[2];
  std::map<short, float> distance_negative_[2];

  // Same fingers state. This state is accumulated as fingers remain the same
  // and it's reset when fingers change.
  std::set<short> palm_;  // tracking ids of known palms
  // These contacts are a subset of palms_ which are marked as palms because
  // they have a large contact size.
  std::set<short> large_palm_;
  // These contacts have moved significantly and shouldn't be considered
  // stationary palms:
  std::set<short> non_stationary_palm_;

  static const unsigned kPointCloseToFinger = 1;
  static const unsigned kPointNotInEdge = 2;
  static const unsigned kPointMoving = 4;
  // tracking ids of known fingers that are not palms, along with the reason(s)
  std::map<short, unsigned> pointing_;


  // tracking ids that were ever close to other fingers.
  std::set<short> was_near_other_fingers_;

  // tracking ids that have ever travelled out of the palm envelope or bottom
  // area.
  std::set<short> fingers_not_in_edge_;

  // Previously input timestamp
  stime_t prev_time_;

  // Maximum pressure above which a finger is considered a palm
  DoubleProperty palm_pressure_;
  // Maximum width_major above which a finger is considered a palm
  DoubleProperty palm_width_;
  // Maximum width_major above which a finger is considered a palm if there are
  // other contacts
  DoubleProperty multi_palm_width_;
  // If a finger was previously classified as palm, but its lifetime max
  // pressure is less than palm_pressure_ * fat_finger_pressure_ratio_,
  // lifetime max width is less than palm_width_ * fat_finger_width_ratio_,
  // and has moved more than fat_finger_min_dist_, we consider it as a fat
  // finger.
  DoubleProperty fat_finger_pressure_ratio_;
  DoubleProperty fat_finger_width_ratio_;
  DoubleProperty fat_finger_min_dist_;
  // The smaller of two widths around the edge for palm detection. Any contact
  // in this edge zone may be a palm, regardless of pressure
  DoubleProperty palm_edge_min_width_;
  // The larger of the two widths. Palms between this and the previous are
  // expected to have pressure linearly increase from 0 to palm_pressure_
  // as they approach this border.
  DoubleProperty palm_edge_width_;
  // If filter_top_edge_ is set, any contact this close to the top edge may be
  // a palm.
  DoubleProperty palm_top_edge_min_width_;
  // Palms in edge are allowed to point if they move fast enough
  DoubleProperty palm_edge_point_speed_;
  // A finger can be added to the palm envelope (and thus not point) after
  // it touches down and until palm_eval_timeout_ [s] time.
  DoubleProperty palm_eval_timeout_;
  // A potential palm (ambiguous contact in the edge of the pad) will be marked
  // a palm if it travels less than palm_stationary_distance_ mm after it's
  // been on the pad for palm_stationary_time_ seconds.
  DoubleProperty palm_stationary_time_;
  DoubleProperty palm_stationary_distance_;
  // For a finger in the palm envelope to be qualified as pointing, it has to
  // move in one direction at least palm_pointing_min_dist_ and no more
  // than palm_pointing_max_reverse_dist_ in the opposite direction.
  DoubleProperty palm_pointing_min_dist_;
  DoubleProperty palm_pointing_max_reverse_dist_;
  // If a palm splits right on the edge, sometimes the pressure readings aren't
  // high enough to identify the palm.  If two fingers on the edge of the pad
  // are closer together than this value, then they are likely a split palm.
  DoubleProperty palm_split_max_distance_;
  // Determines whether we look for palms at the top edge of the touchpad.
  BoolProperty filter_top_edge_;

  DISALLOW_COPY_AND_ASSIGN(PalmClassifyingFilterInterpreter);
};

}  // namespace gestures

#endif  // GESTURES_PALM_CLASSIFYING_FILTER_INTERPRETER_H_
