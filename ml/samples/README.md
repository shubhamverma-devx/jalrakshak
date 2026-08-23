# Bundled SAR sample scenes (demo)

Sen1Floods11 **test split** ke 4 chips — inhe jaan-bujh ke repo mein rakha hai taaki
dashboard ka Satellite tab bina 708 MB dataset download kiye chal jaaye.

**Ye chips training mein KABHI use nahi hue** — ye held-out test split se hain. Model
inhe pehli baar dekhta hai, isliye demo imaandaar hai.

| scene | ground-truth water | kya dikhata hai |
|---|---|---|
| `India_591317` | 46.3% | bada flooded area, Tezpur se 13 km |
| `India_747992` | 44.8% | bada flooded area, Hojai se 7 km (sabse paas ka gaon) |
| `India_1018327` | 14.0% | Brahmaputra ka meander — saaf river channel |
| `India_79637` | 2.0% | bahut kam paani — mushkil case, model ki seema dikhane ke liye |

Sab **Assam (Brahmaputra valley)** mein hain: lat 26.28-26.70, lon 92.88-93.80.

**Licence:** Sen1Floods11, CC-BY 4.0 — Bonafilia et al., CVPR Workshops 2020.
