window.priceFormatter = (formatterParams) => {
    function formatPrice(price, intPartTemplate, fractPartTemplate) {
        if (!intPartTemplate) {
            return price
        }

        if (fractPartTemplate) {
            const fractPartCount = fractPartTemplate.slice(2)
            price = price.toFixed(fractPartCount)
        } else {
            price = price.toFixed(0)
        }

        var formattedString = ""
        const number = price.toString().split('.')

        formattedString += number[0].padStart(intPartTemplate.slice(2), '0')

        if (number[1]) {
            formattedString += '.'
            formattedString += number[1]
        }

        return formattedString
    }

    return (price) => {
        const template = formatterParams.template
        const regExp = new RegExp("\\{price(:#\\d*)?(:#\\d*)?\\}")
        const result = regExp.exec(template)
        if (!result) {
            console.error(`Template ${template} is not valid`)
            return price
        }

        const intPart = result[1] ? result[1] : ""
        const fractPart = result[2] ? result[2] : ""
        return template.replace(regExp, formatPrice(price, intPart, fractPart))
    }
}