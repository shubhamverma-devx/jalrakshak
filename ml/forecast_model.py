"""
forecast_model.py — B2 ke model. Notebook (training) aur forecast.py (inference) dono yahi use karte hain.

============ DO MODEL YAHAN KYUN HAIN ============
`RainfallQuantileLSTM` wo hai jo SHIP hota hai. `DirectClassifierLSTM` wo hai jo pehle
try kiya aur FAIL hua — use hataya nahi, kyunki notebook mein dono ka comparison hai aur
"humne ye bhi try kiya tha, ye kaam nahi aaya" batana natije ka hissa hai.

============ SEEDHA CLASSIFIER KYUN FAIL HUA ============
Pehli koshish thi: sequence -> seedha 3 class (green/yellow/red). Nahi chala:
  - bina class weight ke: hamesha "green" (98.6% accuracy, RED recall 0.000 — bekaar)
  - poore inverse-frequency weight ke saath: RED recall 0.867 par precision 0.066
    (42,995 green dinon mein se 403 pe jhootha RED) — false-alarm machine
Dono soorat mein ek trivial baseline se bura.

============ JO CHALA ============
Label ki asli structure ye hai:
    label = RiskEngine(barish)          <- ye DETERMINISTIC hai, isme kuch seekhna hi nahi
    aur barish aage kya hogi            <- ASLI mushkil sirf yahi hai
To model ko poora kaam dene ke bajaye SIRF mushkil hissa diya: **aage do din ki barish
kitni hogi**. Uske baad wahi asli RiskEngine chalta hai jo dashboard chalata hai.

Faayde:
  - explainable — "35 mm barish ka anumaan, isliye cum3 360 mm, isliye danger mark paar"
  - risk ki definition EK hi jagah rehti hai (RiskEngine), model uska nakal nahi karta
  - quantile se ehtiyaat ka level ek DIAL ban jaata hai (neeche dekho)

============ QUANTILE KYUN, MEAN KYUN NAHI ============
Mean predict karne wala model extremes ko dabaa deta hai (regression to the mean) —
predicted p99 = 15 mm jabki asli p99 = 83 mm. Flood warning mein wo bilkul ulta hai:
jis din sabse zyada barish hoti hai wahi din maayne rakhta hai.
Isliye model kai quantiles predict karta hai aur hum ek OONCHA quantile chunte hain —
matlab "itni barish se zyada hone ka X% chance hai". Kaunsa q — wo VALIDATION set pe
tay hota hai, test pe nahi.
"""

import numpy as np
import torch
import torch.nn as nn

N_CLASSES = 3

# Model in sab quantiles ko ek saath seekhta hai; chunav baad mein validation pe hota hai.
QUANTILES = [0.5, 0.8, 0.9, 0.95, 0.97, 0.99]


class RainfallQuantileLSTM(nn.Module):
    """
    Aage 2 din ki barish ka quantile forecast.

    INPUT : x_seq [B, L, 5], x_static [B, 11]
    OUTPUT: [B, 2, n_quantiles]  — log1p(mm) mein (barish ka distribution bahut skewed hai)
    """

    def __init__(self, n_seq=5, n_static=11, hidden=64, layers=2,
                 dropout=0.2, quantiles=QUANTILES):
        super().__init__()
        self.quantiles = list(quantiles)
        self.n_q = len(self.quantiles)

        self.lstm = nn.LSTM(
            input_size=n_seq, hidden_size=hidden, num_layers=layers,
            batch_first=True, dropout=dropout if layers > 1 else 0.0,
        )
        self.head = nn.Sequential(
            nn.Linear(hidden + n_static, 64),
            nn.ReLU(),
            nn.Dropout(dropout),
            nn.Linear(64, 2 * self.n_q),
        )

    def forward(self, x_seq, x_static):
        out, _ = self.lstm(x_seq)
        z = torch.cat([out[:, -1, :], x_static], dim=1)
        return self.head(z).view(-1, 2, self.n_q)


def pinball_loss(pred, target, quantiles):
    """
    Quantile (pinball) loss.

    INPUT : pred [B, 2, Q], target [B, 2] (log1p mm), quantiles list
    KYUN: MSE mean deta hai. Pinball q-th quantile deta hai — yaani hum model se seedha
    poochh sakte hain "wo barish jisse zyada hone ka (1-q) chance hai".
    """
    q = torch.tensor(quantiles, dtype=pred.dtype, device=pred.device).view(1, 1, -1)
    err = target.unsqueeze(-1) - pred
    return torch.maximum(q * err, (q - 1.0) * err).mean()


class DirectClassifierLSTM(nn.Module):
    """
    Pehli koshish — sequence se seedha risk class. FAIL HUA (upar wajah likhi hai).
    Notebook ke comparison ke liye rakha hai, ship nahi hota.
    """

    def __init__(self, n_seq=5, n_static=11, hidden=64, layers=2, dropout=0.2):
        super().__init__()
        self.lstm = nn.LSTM(n_seq, hidden, layers, batch_first=True,
                            dropout=dropout if layers > 1 else 0.0)

        def head():
            return nn.Sequential(
                nn.Linear(hidden + n_static, 64), nn.ReLU(),
                nn.Dropout(dropout), nn.Linear(64, N_CLASSES),
            )

        self.head24, self.head48 = head(), head()

    def forward(self, x_seq, x_static):
        out, _ = self.lstm(x_seq)
        z = torch.cat([out[:, -1, :], x_static], dim=1)
        return self.head24(z), self.head48(z)


def pick_device():
    """M-series pe MPS, warna CPU. (B1 bhi MPS pe hi train hua tha.)"""
    if torch.backends.mps.is_available():
        return torch.device("mps")
    if torch.cuda.is_available():
        return torch.device("cuda")
    return torch.device("cpu")


def class_weights(y, n_classes=N_CLASSES, power=0.5):
    """Sirf DirectClassifierLSTM ke comparison ke liye — imbalance ka ilaaj."""
    counts = np.bincount(y, minlength=n_classes).astype(np.float64)
    counts[counts == 0] = 1.0
    w = (counts.sum() / counts) ** power
    return torch.tensor(w / w.mean(), dtype=torch.float32)
