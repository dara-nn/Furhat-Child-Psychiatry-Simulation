# Release Notes - 2026-Mar-18

## Custom Case Generation — Now Working
The custom case flow introduced in the Mar-16 release was silently failing on every attempt due to the Gemini thinking model exhausting its token budget before producing output. This has been fixed. Clinicians can now describe any patient they want to practise with and the robot will generate a full character on the fly.

## Face and Voice Mapping

### Pre-Made Cases
| Persona | Face | Voice |
|---|---|---|
| Helmi (12F, Finnish, social anxiety) | White teen girl | White teen girl |
| Lauri (14M, Finnish, depression) | White teen boy | White teen boy |
| Emmi (8F, Finnish, separation anxiety) | Child girl | Emmichildgirl |
| Mei (10F, Chinese, generalized anxiety) | Asian teen girl | Asian teen girl |
| Asha (15F, Indian, perfectionism) | Middle east teen girl | Middle east teen girl |
| Carlos (17M, Mexican, masked depression) | Latin teen boy | Latin teen boy |
| Dmitri (16M, Russian, irritable depression) | Eastern EU teen boy | Eastern EU teen boy |
| Host | Assistant | Assistant |

### Custom-Generated Cases
Generated personas are now matched to face and voice by age, gender, and cultural background:

| Gender | Age | Background | Face | Mask |
|---|---|---|---|---|
| Female | ≥ 12 | Finnish / European | White teen girl | adult |
| Female | ≥ 12 | Eastern EU | Eastern EU teen girl | adult |
| Female | ≥ 12 | Latin | Latin teen girl | adult |
| Female | ≥ 12 | Middle Eastern / Indian | Middle east teen girl | adult |
| Female | ≥ 12 | Asian | Asian teen girl | adult |
| Female | ≥ 12 | African | Black teen girl | adult |
| Female | < 12 | Any | Child girl | child |
| Male | ≥ 12 | Finnish / European | White teen boy | adult |
| Male | ≥ 12 | Eastern EU | Eastern EU teen boy | adult |
| Male | ≥ 12 | Latin | Latin teen boy | adult |
| Male | ≥ 12 | Middle Eastern / Indian | Middle east teen boy | adult |
| Male | ≥ 12 | Asian | Asian teen boy | adult |
| Male | ≥ 12 | African | Black teen boy | adult |
| Male | < 12 | Any | Child boy | child |

Voice is set to match the face name exactly. Previously, custom cases used hardcoded voices that did not correspond to any real ElevenLabs profile.

## Intent Recognition
Exit keyword matching has been tightened — generic single words like "stop", "end", "done", and "finish" have been removed from the exit keyword list. These were falsely triggering exit when users described a patient scenario (e.g. "a child who stopped talking"). Exit now requires explicit phrases.

LLM fallback has been added to all five navigation states so intent is understood from meaning throughout the full conversation flow, not just from keyword matching.

## Emotional Tone
All seven pre-made personas now speak with a condition-appropriate emotional tone. Custom-generated personas receive the same instruction automatically.

## BrowsePersonas — Asha Replaces Dmitri
Asha (15-year-old Indian girl with perfectionism and anxiety) now appears in the browseable case list. Dmitri remains accessible by name.

## Emmi — Behaviour Updated
Emmi gives short, hesitant answers at the start and opens up gradually with warm questioning. Stage directions are blocked and will never be spoken aloud.