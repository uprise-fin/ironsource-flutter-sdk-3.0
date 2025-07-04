import '../ironsource_error.dart';

@Deprecated("This class will be removed in Flutter 4.0.0 version.")
/// iOS Consent View
abstract class IronSourceConsentViewListener {
  /// Reference
  /// - iOS: consentViewDidLoadSuccess
  void consentViewDidLoadSuccess(String consentViewType);

  /// Reference
  /// - iOS: consentViewDidFailToLoadWithError
  void consentViewDidFailToLoad(IronSourceConsentViewError error);

  /// Reference
  /// - iOS: consentViewDidShowSuccess
  void consentViewDidShowSuccess(String consentViewType);

  /// Reference
  /// - iOS: consentViewDidFailToShowWithError
  void consentViewDidFailToShow(IronSourceConsentViewError error);

  /// Reference
  /// - iOS: consentViewDidAccept
  void consentViewDidAccept(String consentViewType);
}
