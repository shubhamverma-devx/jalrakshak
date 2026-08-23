/**
 * Panel — har box ka common frame (header + body). mockup ka `.panel` + `.phead` + `.pbody`.
 *
 * KYUN ek shared component: dashboard mein 6 panel hain. Har jagah wahi header markup
 * copy karne se ek jagah padding badalne pe baaki 5 alag dikhne lagte.
 *
 * INPUT:
 *   title      panel ka naam
 *   subtitle   ek line ka helper — "ye panel hai kis liye" (naya)
 *   Icon       tabler icon component
 *   right      header ke right side ka kuch bhi (count badge waghera)
 *   beforeBody header ke neeche, body se PEHLE, bina scroll ka hissa (filter chips) — naya
 *   footer     body ke neeche, tika hua ("view all") — naya
 *   bodyRef    body element ka ref — caller ko naapna ho ki kitna content kat raha hai
 *   children   body
 *
 * KYUN beforeBody aur footer body ke andar nahi hain: body scroll karti hai. Filter chips
 * ya "view all" agar usi ke andar hote to scroll karte hi gayab ho jaate — jabki wahi do
 * cheezein hain jinki zaroorat tab padti hai jab list lambi ho.
 */
export default function Panel({
  title,
  subtitle,
  Icon,
  right,
  children,
  style,
  bodyStyle,
  bodyClass,
  beforeBody,
  footer,
  bodyRef,
}) {
  return (
    <div className="panel" style={style}>
      <div className={`phead${subtitle ? ' with-sub' : ''}`}>
        <span className="ptitle">
          {Icon && <Icon className="ti" />}
          {subtitle ? (
            <span className="ptitle-wrap">
              <span>{title}</span>
              <span className="psub">{subtitle}</span>
            </span>
          ) : (
            title
          )}
        </span>
        {right}
      </div>

      {beforeBody}

      <div ref={bodyRef} className={`pbody${bodyClass ? ' ' + bodyClass : ''}`} style={bodyStyle}>
        {children}
      </div>

      {footer}
    </div>
  )
}

/**
 * Empty — jab data hai hi nahi (koi SOS nahi, koi alert nahi).
 * KYUN: khaali panel "load ho raha hai" jaisa lagta hai. Saaf likhna behtar hai ki
 * kuch hai hi nahi — aur flood dashboard mein "koi SOS nahi" achhi khabar hai.
 */
export function Empty({ Icon, children }) {
  return (
    <div className="empty">
      {Icon && <Icon className="ti" />}
      <span>{children}</span>
    </div>
  )
}
