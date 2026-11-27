# Call sounds

## InterCom's own

The join and leave chimes, the ringback of an outgoing call and the cues for the user's own mute, unmute, deafen and undeafen are InterCom's, the same
files as the desktop app's, copied unchanged. They are not recordings: scripts work them out from sine waves
(`scripts/intercom-sounds.py` in the Element Call fork for the chimes, `apps/desktop/intercom/gen-sounds.py` in the
Element Web fork for the cues), and they are covered by the licence of the InterCom repositories. None is taken from
another product. `CallSoundEffectAssetsTest` holds their hashes too.

| File in `call/impl/src/main/res/raw/` | Source | SHA-256 |
| :--- | :--- | :--- |
| `element_call_intercom_join.ogg` | intercom-element-call `src/sound/intercom_join.ogg` | `36561404f74404918037bd82603d9253ab1711875bfdea5760b74d52699840ca` |
| `element_call_intercom_leave.ogg` | intercom-element-call `src/sound/intercom_leave.ogg` | `af07fc52adf088c28427e5daffc86fd9b16f7a02e0e1a576659773ec26e0597e` |
| `element_call_intercom_ringback.ogg` | intercom-element-web `apps/web/res/media/intercom/ringback.ogg` | `1d8cc60f918eaab1e7be9825dc45df13f782baf91e6a58965407f644fe2232a7` |
| `element_call_intercom_mute.ogg` | intercom-element-web `apps/web/res/media/intercom/mute.ogg` | `e108a4deb7369e2fd31c532a12f0dfafa2dea11e952cb2d5aa9dde2991df81a3` |
| `element_call_intercom_unmute.ogg` | intercom-element-web `apps/web/res/media/intercom/unmute.ogg` | `50b8aca8e1bd74cf5dae0e06390c41c252d3b559afcbb811fbfd7bd1e843d247` |
| `element_call_intercom_deafen.ogg` | intercom-element-web `apps/web/res/media/intercom/deafen.ogg` | `28b72917fab79dc3121797d2a77ffc7a5d3fc56385ee913922f63d881fa74996` |
| `element_call_intercom_undeafen.ogg` | intercom-element-web `apps/web/res/media/intercom/undeafen.ogg` | `a680e5e2d625cda49283df13693fe3a0d094eb93c225dc8de2a161db2576491e` |

Element Call's own join, leave and ringback sounds (`element_call_join_call.mp3`, `element_call_left_call.mp3`,
`element_call_ringtone.mp3`) are no longer in the library.

## Element Call's

Element Call web's own files, copied unchanged so a call sounds the same on every client in it (feature-hq spec 024
R3, spec 009 R34, spec 030 R23 and R25, spec 037 R9).
Do not re-encode them: the hashes are how a reviewer checks they still match web and iOS, and `CallSoundEffectAssetsTest`
fails if they drift.

| File in `call/impl/src/main/res/raw/` | Source | SHA-256 |
| :--- | :--- | :--- |
| `element_call_raise_hand.mp3` | `element-call` `src/sound/raise_hand.mp3` at 25c4f14d | `0601948ad6e4937cc6bca126f0decb2218037f39f6f7dc99cee359efdb66212b` |
| `element_call_call_declined.mp3` | `element-call` `src/sound/call_declined.mp3` at 956671eef | `bb8d93337c6d8a391fbc7750ce5fb955413ba084183a76c22aba5016f8d0e06b` |
| `element_call_call_timeout.mp3` | `element-call` `src/sound/call_timeout.mp3` at 956671eef | `c0aed0c09a85d580824847988e6868121c1329997cf8a3df88a42895adbf3ddd` |
| `element_call_reaction_baduntss.mp3` | `element-call` `src/sound/reactions/baduntss.mp3` at eb44fd545 | `48c30e2259598e3de9880d95b1075fcb7aa35a9b41ab94447f22534dc3b3d1e0` |
| `element_call_reaction_bell.mp3` | `element-call` `src/sound/reactions/bell.mp3` at eb44fd545 | `d489ccd5e386072e9db47c9a332ef4f33130cf940e1ddde55d062835ae197bb8` |
| `element_call_reaction_cat.mp3` | `element-call` `src/sound/reactions/cat.mp3` at eb44fd545 | `cbf49e888bcf2213145fa43045c3493746c8b2fc3cbebadf6727a244ec60ac4d` |
| `element_call_reaction_clap.mp3` | `element-call` `src/sound/reactions/clap.mp3` at eb44fd545 | `b927e4bd51c781fb0232b7430b13aa56cf7e9e1bf8a3b049a0011312ce5c8830` |
| `element_call_reaction_crickets.mp3` | `element-call` `src/sound/reactions/crickets.mp3` at eb44fd545 | `f7949e8f092a8653f3da9f4d117be8cbc61b68c580bb43f610838df0061da8de` |
| `element_call_reaction_deer.mp3` | `element-call` `src/sound/reactions/deer.mp3` at eb44fd545 | `e9fdba54df8e31091c311b61bc16413accaebfc5b3bf18c6b7761577ac354c6c` |
| `element_call_reaction_dog.mp3` | `element-call` `src/sound/reactions/dog.mp3` at eb44fd545 | `0b0ce7ea208cd900c11e4743e9c25a6633d7d6e750bd8d5d34f1d3119a469260` |
| `element_call_reaction_generic.mp3` | `element-call` `src/sound/reactions/generic.mp3` at eb44fd545 | `748e6d03a559ac909d04b9fa907fca3424c6bf00aa45bcad3f6a07238e58f6cb` |
| `element_call_reaction_lightbulb.mp3` | `element-call` `src/sound/reactions/lightbulb.mp3` at eb44fd545 | `2471aac761484adb8c2485954016b93af907d14a4d03afdcffe0a5749f489bb0` |
| `element_call_reaction_party.mp3` | `element-call` `src/sound/reactions/party.mp3` at eb44fd545 | `3ab05da72c326143027e1bae16e005512e94eb8985d978a10eb9016f088a6b79` |
| `element_call_reaction_rock.mp3` | `element-call` `src/sound/reactions/rock.mp3` at eb44fd545 | `337166ae2fb7cac96b8c5fd6d2872dfd88105fc59363101846f3d55c30986531` |
| `element_call_reaction_wave.mp3` | `element-call` `src/sound/reactions/wave.mp3` at eb44fd545 | `4391d019809c9951333cd8ab9f5329b039602334bb55f5855b7852321109a6ac` |

The call sounds were created by Element (`element-call` `src/sound/LICENCE.md`). The mp3s rather than web's `.ogg` twins because iOS cannot
decode Ogg Vorbis, and both platforms quote the same files.

The reaction sounds are licensed as web's `src/sound/LICENCE.md` says: Pixabay for most, Element's own for `rock`,
`wave` and `baduntss`; `deer` is not listed there.

The reaction sounds play at a fixed gain each, which brings them to the join sound's loudness (`REACTION_SOUNDS` in
`CallSoundEffectRenderer`): as web ships them they span 15 LU, and the loudest distort a phone's speaker.
